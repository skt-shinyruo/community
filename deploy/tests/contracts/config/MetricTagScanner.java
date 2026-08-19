import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class MetricTagScanner {
  private enum TagKeyArguments {
    KEY_VALUE_PAIRS {
      @Override boolean contains(int argumentIndex) {
        return argumentIndex % 2 == 0;
      }
    },
    SINGLE_KEY {
      @Override boolean contains(int argumentIndex) {
        return argumentIndex == 0;
      }
    },
    METER_NAME_THEN_KEY_VALUE_PAIRS {
      @Override boolean contains(int argumentIndex) {
        return argumentIndex > 0 && argumentIndex % 2 == 1;
      }
    };

    abstract boolean contains(int argumentIndex);
  }

  private enum MetricInvocation {
    TAGS_FACTORY("Tags.of", null, TagKeyArguments.KEY_VALUE_PAIRS),
    TAGS_METHOD(null, "tags", TagKeyArguments.KEY_VALUE_PAIRS),
    TAG_FACTORY("Tag.of", null, TagKeyArguments.SINGLE_KEY),
    TAG_METHOD(null, "tag", TagKeyArguments.SINGLE_KEY),
    COUNTER_METHOD(null, "counter", TagKeyArguments.METER_NAME_THEN_KEY_VALUE_PAIRS),
    TIMER_METHOD(null, "timer", TagKeyArguments.METER_NAME_THEN_KEY_VALUE_PAIRS),
    SUMMARY_METHOD(null, "summary", TagKeyArguments.METER_NAME_THEN_KEY_VALUE_PAIRS),
    GAUGE_METHOD(null, "gauge", TagKeyArguments.METER_NAME_THEN_KEY_VALUE_PAIRS);

    private final String selectSuffix;
    private final String methodName;
    private final TagKeyArguments tagKeyArguments;

    MetricInvocation(String selectSuffix, String methodName, TagKeyArguments tagKeyArguments) {
      this.selectSuffix = selectSuffix;
      this.methodName = methodName;
      this.tagKeyArguments = tagKeyArguments;
    }

    private boolean matches(String select, String method) {
      return selectSuffix != null ? select.endsWith(selectSuffix) : methodName.equals(method);
    }

    private static MetricInvocation resolve(String select, String method) {
      for (MetricInvocation invocation : values()) {
        if (invocation.matches(select, method)) {
          return invocation;
        }
      }
      return null;
    }
  }

  private static String methodName(MethodInvocationTree call) {
    Tree select = call.getMethodSelect();
    return select instanceof MemberSelectTree member
        ? member.getIdentifier().toString() : select.toString();
  }

  private static void scan(String file, CompilationUnitTree unit, Trees trees) {
    SourcePositions positions = trees.getSourcePositions();
    new TreePathScanner<Void, Void>() {
      @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
        String select = call.getMethodSelect().toString();
        MetricInvocation invocation = MetricInvocation.resolve(select, methodName(call));
        if (invocation == null) {
          return super.visitMethodInvocation(call, unused);
        }

        List<? extends ExpressionTree> arguments = call.getArguments();
        for (int index = 0; index < arguments.size(); index++) {
          if (!invocation.tagKeyArguments.contains(index)) {
            continue;
          }
          ExpressionTree argument = arguments.get(index);
          if (!(argument instanceof LiteralTree literal)
              || literal.getKind() != Tree.Kind.STRING_LITERAL) {
            continue;
          }
          long start = positions.getStartPosition(unit, argument);
          if (start < 0) {
            continue;
          }
          long line = unit.getLineMap().getLineNumber(start);
          System.out.println(file + "\t" + line + "\t" + literal.getValue() + "\t" + select);
        }
        return super.visitMethodInvocation(call, unused);
      }
    }.scan(unit, null);
  }

  public static void main(String[] files) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("JDK compiler is required");
    }
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
      Iterable<? extends JavaFileObject> sources = manager.getJavaFileObjects(files);
      JavacTask task = (JavacTask) compiler.getTask(
          null, manager, null, List.of("-proc:none"), null, sources);
      Trees trees = Trees.instance(task);
      for (CompilationUnitTree unit : task.parse()) {
        scan(unit.getSourceFile().getName(), unit, trees);
      }
    }
  }
}
