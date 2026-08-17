#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${repo_root}"

fail() {
  echo "observability contract check failed: $*" >&2
  exit 1
}

contract_dir="deploy/observability/contracts"
handbook="docs/handbook/observability.md"
metric_scan="$(mktemp)"
trap 'rm -f "${metric_scan}"' EXIT

required_files=(
  "${handbook}"
  "backend/community-app/src/main/resources/application.yml"
  "backend/community-gateway/src/main/resources/application.yml"
  "backend/community-im-gateway/src/main/resources/application.yml"
  "backend/community-im/im-core/src/main/resources/application.yml"
  "backend/community-im/im-realtime/src/main/resources/application.yml"
  "backend/community-oss/src/main/resources/application.yml"
  "${contract_dir}/README.md"
  "${contract_dir}/required-resource-fields.txt"
  "${contract_dir}/metric-families.txt"
  "${contract_dir}/allowed-metric-dimensions.txt"
  "${contract_dir}/forbidden-observability-fields.txt"
  "${contract_dir}/manual-span-names.txt"
  "deploy/observability/production/README.md"
  "deploy/observability/production/collector-agent.yml"
  "deploy/observability/production/collector-gateway.yml"
)

for file in "${required_files[@]}"; do
  if [ ! -s "${file}" ]; then
    fail "required file missing or empty: ${file}"
  fi
done

backend_configs=(
  backend/community-app/src/main/resources/application.yml
  backend/community-gateway/src/main/resources/application.yml
  backend/community-im-gateway/src/main/resources/application.yml
  backend/community-im/im-core/src/main/resources/application.yml
  backend/community-im/im-realtime/src/main/resources/application.yml
  backend/community-oss/src/main/resources/application.yml
)

for config in "${backend_configs[@]}"; do
  if ! rg -n -F 'console: logstash' "${config}" >/dev/null; then
    fail "structured logstash console formatter missing from ${config}"
  fi
  for field in service.name service.version service.namespace deployment.environment; do
    if ! rg -n -F '"['"${field}"']":' "${config}" >/dev/null; then
      fail "structured log field ${field} missing from ${config}"
    fi
  done
done

if rg -n 'logstash-logback-encoder|community-common-observability' \
  backend/*/pom.xml backend/community-im/*/pom.xml >/dev/null; then
  fail "retired custom logging dependencies remain in backend deployables"
fi

for file in deploy/observability/production/collector-agent.yml deploy/observability/production/collector-gateway.yml; do
  for token in receivers processors exporters service; do
    if ! rg -n "^${token}:" "${file}" >/dev/null; then
      fail "collector template ${file} missing top-level ${token}"
    fi
  done
  if ! rg -n "^  pipelines:" "${file}" >/dev/null; then
    fail "collector template ${file} missing service pipelines"
  fi
done

if ! rg -n '^  tail_sampling:' deploy/observability/production/collector-gateway.yml >/dev/null; then
  fail "gateway collector template must include tail_sampling"
fi

if ! rg -n '^  attributes/drop_sensitive:' deploy/observability/production/collector-gateway.yml >/dev/null; then
  fail "gateway collector template must include sensitive attribute deletion"
fi

require_gateway_redaction_delete() {
  local redaction_key="$1"

  if ! awk -v required_key="${redaction_key}" '
    /^  attributes\/drop_sensitive:$/ {
      in_processor = 1
      next
    }
    in_processor && /^  [^[:space:]][^:]*:$/ {
      exit found ? 0 : 1
    }
    in_processor && /^    actions:$/ {
      in_actions = 1
      next
    }
    in_actions && /^    [^[:space:]][^:]*:$/ {
      exit found ? 0 : 1
    }
    in_actions && /^      - key: / {
      pending_key = ($0 == "      - key: " required_key)
      next
    }
    in_actions && pending_key && /^        action: delete$/ {
      found = 1
      exit 0
    }
    END {
      exit found ? 0 : 1
    }
  ' deploy/observability/production/collector-gateway.yml; then
    fail "gateway collector template must delete sensitive attribute: ${redaction_key}"
  fi
}

reject_gateway_redaction_delete() {
  local redaction_key="$1"

  if awk -v rejected_key="${redaction_key}" '
    /^  attributes\/drop_sensitive:$/ {
      in_processor = 1
      next
    }
    in_processor && /^  [^[:space:]][^:]*:$/ {
      exit found ? 0 : 1
    }
    in_processor && /^    actions:$/ {
      in_actions = 1
      next
    }
    in_actions && /^    [^[:space:]][^:]*:$/ {
      exit found ? 0 : 1
    }
    in_actions && /^      - key: / {
      pending_key = ($0 == "      - key: " rejected_key)
      next
    }
    in_actions && pending_key && /^        action: delete$/ {
      found = 1
      exit 0
    }
    END {
      exit found ? 0 : 1
    }
  ' deploy/observability/production/collector-gateway.yml; then
    fail "gateway collector template must preserve correlation attribute: ${redaction_key}"
  fi
}

while IFS= read -r redaction_key; do
  case "${redaction_key}" in
    '' | '#'* | 'trace.id' | 'span.id')
      continue
      ;;
  esac
  require_gateway_redaction_delete "${redaction_key}"
done <"${contract_dir}/forbidden-observability-fields.txt"

for redaction_key in http.request.body http.response.body db.statement.parameters redis.key messaging.message.body; do
  require_gateway_redaction_delete "${redaction_key}"
done

for correlation_key in trace.id span.id; do
  reject_gateway_redaction_delete "${correlation_key}"
done

unfinished_pattern='TB''D|TO''DO|FIX''ME|place''holder|to be ''decided'
if rg -n "${unfinished_pattern}" "${handbook}" "${contract_dir}" >/dev/null; then
  rg -n "${unfinished_pattern}" "${handbook}" "${contract_dir}" >&2
  fail "observability docs or contracts contain unfinished marker text"
fi

for heading in \
  '## SLO/SLI Catalog' \
  '## Shared Resource Fields' \
  '## Metrics Contract' \
  '## Trace Contract' \
  '## Instrumentation Boundaries' \
  '## Alert Priority' \
  '## Governance'
do
  if ! rg -n "^${heading}$" "${handbook}" >/dev/null; then
    fail "missing handbook heading: ${heading}"
  fi
done

metric_sources=()
while IFS= read -r file; do
  metric_sources+=("${file}")
done < <(rg -l 'io\.micrometer\.core\.instrument|Counter\.builder|Timer\.builder|Gauge\.builder|DistributionSummary\.builder|MeterRegistry' backend || true)

if [ "${#metric_sources[@]}" -gt 0 ]; then
  scanner_dir="$(mktemp -d)"
  trap 'rm -f "${metric_scan}"; rm -rf "${scanner_dir}"' EXIT
  cat >"${scanner_dir}/MetricTagScanner.java" <<'JAVA'
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
import javax.lang.model.element.Name;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.util.List;

final class MetricTagScanner {
  private static String methodName(MethodInvocationTree call) {
    Tree select = call.getMethodSelect();
    return select instanceof MemberSelectTree member
        ? member.getIdentifier().toString() : select.toString();
  }

  private static boolean metricBuilder(String method) {
    return List.of("counter", "timer", "summary", "gauge").contains(method);
  }

  private static void scan(String file, CompilationUnitTree unit, Trees trees) {
    SourcePositions positions = trees.getSourcePositions();
    new TreePathScanner<Void, Void>() {
      @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
        String select = call.getMethodSelect().toString();
        String method = methodName(call);
        int mode = select.endsWith("Tags.of") || method.equals("tags") ? 1
            : select.endsWith("Tag.of") || method.equals("tag") ? 2
            : metricBuilder(method) ? 3 : 0;
        if (mode != 0) {
          List<? extends ExpressionTree> arguments = call.getArguments();
          for (int i = 0; i < arguments.size(); i++) {
            if ((mode == 1 && i % 2 != 0) || (mode == 3 && i % 2 != 1) || (mode == 2 && i != 0)) {
              continue;
            }
            ExpressionTree argument = arguments.get(i);
            if (!(argument instanceof LiteralTree literal)
                || literal.getKind() != Tree.Kind.STRING_LITERAL) {
              continue;
            }
            long start = positions.getStartPosition(unit, argument);
            if (start < 0) continue;
            long line = unit.getLineMap().getLineNumber(start);
            System.out.println(file + "\t" + line + "\t" + literal.getValue() + "\t" + select);
          }
        }
        return super.visitMethodInvocation(call, unused);
      }
    }.scan(unit, null);
  }

  public static void main(String[] files) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) throw new IllegalStateException("JDK compiler is required");
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
      Iterable<? extends JavaFileObject> sources = manager.getJavaFileObjects(files);
      JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
          List.of("-proc:none"), null, sources);
      Trees trees = Trees.instance(task);
      for (CompilationUnitTree unit : task.parse()) {
        JavaFileObject source = unit.getSourceFile();
        scan(source.getName(), unit, trees);
      }
    }
  }
}
JAVA
  javac --release 17 -d "${scanner_dir}" "${scanner_dir}/MetricTagScanner.java"
  java -cp "${scanner_dir}" MetricTagScanner "${metric_sources[@]}" >"${metric_scan}"
fi

while IFS= read -r forbidden; do
  case "${forbidden}" in
    '' | '#'*)
      continue
      ;;
  esac
  if awk -F '\t' -v forbidden="${forbidden}" '
    $3 == forbidden {
      printf "%s:%s: forbidden metric tag key %s in %s\n", $1, $2, $3, $4 > "/dev/stderr"
      found = 1
    }
    END {
      exit found ? 0 : 1
    }
  ' "${metric_scan}"; then
    fail "forbidden observability field appears as a metric tag key: ${forbidden}"
  fi
done <"${contract_dir}/forbidden-observability-fields.txt"

for required_field in service.name service.version service.namespace deployment.environment; do
  if ! rg -n "^${required_field}$" "${contract_dir}/required-resource-fields.txt" >/dev/null; then
    fail "required resource field missing from contract: ${required_field}"
  fi
done
