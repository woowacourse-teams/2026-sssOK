"""Bounded photometric retry against frozen seeds; no new identity groups."""
from pathlib import Path
import time
import numpy as np
from .subject_evidence import contains_face


def overlap(a, b):
    x,y,w,h=a; xx,yy,ww,hh=b
    area=max(0,min(x+w,xx+ww)-max(x,xx))*max(0,min(y+h,yy+hh)-max(y,yy))
    return area/max(w*h+ww*hh-area,1e-12)


def transform_images(image):
    import cv2
    gray=cv2.cvtColor(image,cv2.COLOR_BGR2GRAY); lo,hi=np.percentile(gray,[1,99])
    stretched=np.clip((image.astype(np.float32)-lo)*255/max(hi-lo,1),0,255).astype(np.uint8)
    lab=cv2.cvtColor(image,cv2.COLOR_BGR2LAB)
    lab[:,:,0]=cv2.createCLAHE(clipLimit=2.,tileGridSize=(8,8)).apply(lab[:,:,0])
    gamma=cv2.LUT(image,np.array([round((i/255)**.5*255) for i in range(256)],dtype=np.uint8))
    return {'stretch':stretched,'clahe':cv2.cvtColor(lab,cv2.COLOR_LAB2BGR),'gamma':gamma}


def select_proposals(candidates, faces, groups):
    eligible=[x for x in candidates if x['score']>=.65 and x['margin']>=.15]
    # Different photometric views are not independent identity references.
    # They can, however, expose contradictions that require abstention.
    eligible=[x for x in eligible if not any(y['image_id']==x['image_id'] and y['target_face_id']==x['target_face_id']
                    and (y['group']!=x['group'] or overlap(x['bbox'],y['bbox'])<.6) for y in eligible)]
    used=set(); occupied={(gi,faces[fid]['image_id']) for gi,g in enumerate(groups) for fid in g['face_ids']}
    accepted=[]
    for x in sorted(eligible,key=lambda x:(-x['score'],x['method'])):
        key=(x['image_id'],x['target_face_id'])
        if key in used or (x['group'],x['image_id']) in occupied:continue
        accepted.append(x);used.add(key);occupied.add((x['group'],x['image_id']))
    return accepted


def filter_uncertain_detector_bodies(additions, bodies, faces, features, groups, identities):
    """Additional negative gate; never replaces the earlier body veto rule."""
    kept=[]; rejected=[]; by_id={b['body_id']:b for b in bodies}
    for a in additions:
        ff=[f for f in faces if contains_face(by_id[a['body_id']],f)]
        reject=False
        if len(ff)==1:
            f=ff[0];q=f['quality'];fail=set(q['seed_failures'])
            if q.get('sharpness',0)>=40:fail.discard('sharpness')
            if q.get('score',0)>=.85:fail.discard('score')
            if not fail:
                refs={}
                for fid in groups[a['group']]['seed_face_ids']:
                    pid=identities[faces[fid]['image_id']]
                    if pid==identities[f['image_id']]:continue
                    refs[pid]=max(refs.get(pid,-1),float(features[f['face_id']]@features[fid]))
                reject=len(refs)>=2 and max(refs.values())<.30
        if reject:rejected.append({'body_id':a['body_id'],'reason':'contradictory_face_with_uncertain_detector'})
        else:kept.append(a)
    return kept,rejected


def gather_candidates(photos, output, assets, faces, features, groups, identities):
    if not groups:return [],{'attempted_photos':0}
    import cv2
    locked={fid for g in groups for fid in g['face_ids']}
    candidates=[];attempted=0;errors=[];detector=recognizer=None
    for photo in photos:
        pid=photo['image_id'];fs=[f for f in faces if f['image_id']==pid]
        targets=[f for f in fs if f['face_id'] not in locked and (f['quality']['contrast']<18 or f['quality']['sharpness']<30)]
        if fs and not targets:continue
        try:
            image=cv2.imread(str(Path(output)/photo['prepared_path']))
            if image is None:raise OSError('prepared image decode failed')
            gray=cv2.cvtColor(image,cv2.COLOR_BGR2GRAY)
            if not fs and (gray.std()>=25 or np.percentile(gray,99)-np.percentile(gray,1)>80):continue
            if detector is None:
                from .image_features import face_quality
                detector=cv2.FaceDetectorYN.create(str(assets['yunet']),'',(320,320),.75,.3,5000)
                recognizer=cv2.FaceRecognizerSF.create(str(assets['sface']),'')
            attempted+=1;h,w=image.shape[:2]
            for method,adjusted in transform_images(image).items():
                detector.setInputSize((w,h));_,detected=detector.detect(adjusted)
                rows=[r for r in detected if min(r[2:4])>=32] if detected is not None else []
                if not fs and len(rows)!=1:continue
                largest=max((float(r[2]*r[3]) for r in rows),default=1)
                for row in rows:
                    target=None
                    if fs:
                        best=max(targets,key=lambda f:overlap(f['bbox'],row[:4]))
                        if overlap(best['bbox'],row[:4])<.6:continue
                        target=best['face_id']
                    aligned=recognizer.alignCrop(adjusted,row);q,_=face_quality(row,aligned,(w,h),largest)
                    if any(q[k]<v for k,v in [('min_size',64),('score',.90),('contrast',18),('sharpness',60),('eye_width',.18)]) or q['nose_offset']>.8:continue
                    feature=recognizer.feature(aligned).flatten().copy();feature/=max(float(np.linalg.norm(feature)),1e-12)
                    options=[]
                    for gi,g in enumerate(groups):
                        scores={}
                        for fid in g['seed_face_ids']:
                            ref=identities[faces[fid]['image_id']]
                            if ref==identities[pid]:continue
                            value=float(feature@features[fid])
                            if ref not in scores or value>scores[ref][0]:scores[ref]=(value,fid)
                        top=sorted(scores.values(),reverse=True)[:2]
                        if top:options.append({'group':gi,'score':float(np.mean([x[0] for x in top])),'references':[x[1] for x in top]})
                    options.sort(key=lambda x:x['score'],reverse=True)
                    if not options:continue
                    a=options[0];a['margin']=a['score']-(options[1]['score'] if len(options)>1 else -1)
                    if len(a['references'])<2:continue
                    if pid in {faces[fid]['image_id'] for fid in groups[a['group']]['face_ids']}:continue
                    candidates.append({'image_id':pid,'target_face_id':target,'method':method,'bbox':row[:4].tolist(),
                                       'quality':q,'feature':feature,'aligned':aligned,**a})
        except (OSError,ValueError,cv2.error) as exc:
            errors.append({'image_id':pid,'reason':'exposure_retry_failed','error_type':type(exc).__name__})
    return candidates,{'attempted_photos':attempted,'errors':errors}


def recover_exposure_faces(photos, output, assets, faces, features, bodies, groups, additions, identities):
    started=time.perf_counter()
    candidates,evidence=gather_candidates(photos,output,assets,faces,features,groups,identities)
    proposals=select_proposals(candidates,faces,groups)
    evidence['accepted']=[];evidence['rejected_bodies']=[]
    by_photo={p['image_id']:p for p in photos}
    by_body={b['body_id']:b for b in bodies}
    for x in proposals:
        fid=x['target_face_id']
        if fid is None:
            import cv2
            fid=len(faces);crop=f'faces/{fid:06d}.jpg'
            if not cv2.imwrite(str(Path(output)/crop),x['aligned']):raise OSError('Cannot write recovered crop')
            q=dict(x['quality']);q['seed_failures']=sorted(set(q['seed_failures'])|{'exposure_recovery_not_seed'})
            faces.append({'face_id':fid,'image_id':x['image_id'],'bbox':x['bbox'],'quality':q,'crop':crop,'source':'exposure_recovery'})
            features=np.vstack([features,x['feature']]);by_photo[x['image_id']]['face_ids'].append(fid)
        groups[x['group']]['face_ids'].append(fid)
        newface={'face_id':fid,'image_id':x['image_id'],'bbox':x['bbox']}
        kept=[]
        for a in additions:
            body=by_body[a['body_id']]
            covered=[f for f in faces if contains_face(body,f)]
            if a['group']!=x['group'] and contains_face(body,newface) and len(covered)==1:
                evidence['rejected_bodies'].append({'body_id':a['body_id'],'reason':'conflicting_exposure_recovered_face'})
            else:kept.append(a)
        additions=kept
        evidence['accepted'].append({**{k:v for k,v in x.items() if k not in ('feature','aligned')},'face_id':fid})
    evidence['seconds']=round(time.perf_counter()-started,4)
    return features,additions,evidence
