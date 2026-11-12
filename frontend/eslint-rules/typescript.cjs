/** 팀 규칙: 이름을 붙여 선언한 함수·훅은 반환 타입 명시, 컴포넌트·인라인 콜백은 추론. */
module.exports = {
  rules: {
    "explicit-return-type": {
      meta: {
        type: "suggestion",
        schema: [],
        messages: { missing: "일반 함수와 커스텀 훅에는 반환 타입을 명시하세요." },
      },
      create(context) {
        function check(node) {
          if (node.returnType) return;
          const parent = node.parent;
          const name =
            node.id?.name ?? (parent.type === "VariableDeclarator" ? parent.id.name : undefined);
          if (!name || (/^[A-Z]/.test(name) && /\.tsx$/.test(context.filename))) return;
          context.report({ node, messageId: "missing" });
        }
        return {
          FunctionDeclaration: check,
          FunctionExpression: check,
          ArrowFunctionExpression: check,
        };
      },
    },
    "exception-description": {
      meta: {
        type: "problem",
        schema: [],
        messages: {
          reason: "ESLint 예외는 해당 줄에만 적용하고 -- 뒤에 구체적인 사유를 작성하세요.",
        },
      },
      create(context) {
        return {
          Program() {
            for (const comment of context.sourceCode.getAllComments()) {
              const text = comment.value.trim();
              if (!/^eslint-disable/.test(text)) continue;
              if (
                !/^eslint-disable-next-line\s+[@\w/,-]+(?:\s*,\s*[@\w/-]+)*\s+--\s+\S.{5,}/.test(
                  text,
                )
              )
                context.report({ loc: comment.loc, messageId: "reason" });
            }
          },
        };
      },
    },
  },
};
