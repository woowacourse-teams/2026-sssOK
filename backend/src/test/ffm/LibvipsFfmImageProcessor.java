package com.sssok.renewal.outgoing.processing.image;
import app.photofox.vipsffm.*;
import app.photofox.vipsffm.enums.*;
import java.lang.foreign.Arena;
import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.*;
public class LibvipsFfmImageProcessor implements ImageDerivativeEngine {
 public LibvipsFfmImageProcessor() { Vips.init(); Vips.disableOperationCache(); }
 public boolean supports(byte[] s, DerivativeSpec t, DerivativeSpec p) {
  return s.length>=3 && (s[0]&255)==255 && (s[1]&255)==216 && (s[2]&255)==255 && t.format()==DerivativeFormat.WEBP && (p==null || p.format()==DerivativeFormat.WEBP);
 }
 public DerivedImages derive(byte[] source, DerivativeSpec t, DerivativeSpec p) {
  validate(t); if(p!=null) validate(p);
  if(source.length==0 || source.length>150*1024*1024) throw new IllegalArgumentException("JPEG input size exceeds limit");
  try(var arena=Arena.ofConfined()) {
   var blob=VBlob.newFromBytes(arena,source);
   var header=VImage.jpegloadBuffer(arena,blob,VipsOption.Enum("access",VipsAccess.ACCESS_SEQUENTIAL));
   int w=header.getWidth(), h=header.getHeight();
   Integer o=header.getInt("orientation");
   if(o!=null && o>=5 && o<=8) { int swap=w; w=h; h=swap; }
   if(w<=0 || h<=0 || (long)w*h>100000000) throw new IllegalArgumentException("JPEG dimensions exceed processing limit");
   int tw=Math.min(Math.max(t.maxWidth(),p==null?0:p.maxWidth()),w), th=height(w,h,tw);
   if(tw>16383 || th>16383 || (long)tw*th>16000000) throw new IllegalArgumentException("JPEG dimensions exceed processing limit");
   var shared=VImage.thumbnailBuffer(arena,blob,tw,VipsOption.Int("height",th),VipsOption.Enum("size",VipsSize.SIZE_FORCE),VipsOption.String("option_string","fail_on=error")).copyMemory();
   return new DerivedImages(w,h,encode(shared,w,h,t),p==null?null:encode(shared,w,h,p));
  }
 }
 private static int height(int w,int h,int target) { return Math.max(1,(int)Math.round((double)h*target/w)); }
 private static DerivedImage encode(VImage shared,int w,int h,DerivativeSpec s) {
  int tw=Math.min(s.maxWidth(),w),th=height(w,h,tw);
  var resized=shared.resize((double)tw/shared.getWidth(),VipsOption.Double("vscale",(double)th/shared.getHeight()),VipsOption.Enum("kernel",VipsKernel.KERNEL_LINEAR));
  return new DerivedImage(resized.webpsaveBuffer(VipsOption.Int("Q",Math.round(s.quality()*100)),VipsOption.Int("effort",0),VipsOption.Boolean("strip",true)).getBytes(),DerivativeFormat.WEBP);
 }
 private static void validate(DerivativeSpec s) {
  if(s.maxWidth()<=0 || s.maxWidth()>16383 || !Float.isFinite(s.quality()) || s.quality()<=0 || s.quality()>1 || s.format()!=DerivativeFormat.WEBP) throw new IllegalArgumentException("Invalid derivative specification");
 }
}
