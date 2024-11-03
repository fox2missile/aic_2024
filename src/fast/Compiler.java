package fast;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.util.Map;


public class Compiler {
    static int i = 0;

    private static void loopFinder(Node n) {
        n.accept(new VoidVisitorAdapter<Object>() {
            @Override
            public void visit(WhileStmt n, Object arg) {
                System.out.printf("%d: loop found: %s", i++, n);
            }
        }, null);
        n.getChildNodes().forEach(n_ -> loopFinder(n_));
    }

    private static void loopFinder(CompilationUnit cu) {
        cu.getChildNodes().forEach(n -> loopFinder(n));
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> codeBase = CodeBaseUtil.readFiles("src/kyuu");
        CompilationUnit parsed = StaticJavaParser.parse(codeBase.get("src/kyuu/fast/PriorityQueue.java"));
        // loopFinder(parsed);
        parsed.accept(new VoidVisitorAdapter<Object>() {
            @Override
            public void visit(WhileStmt n, Object arg) {
                super.visit(n, arg);

                if (n.getComment().isPresent()) {
                    if (n.getComment().toString().contains("@unroll")) {
                        System.out.printf("%d: unroll loop found: %s\n", i++, n.getCondition());
                        
                    }
                }
                
            }
        }, null);
    }
}
