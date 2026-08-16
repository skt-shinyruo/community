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
  perl - "${metric_sources[@]}" >"${metric_scan}" <<'PERL'
use strict;
use warnings;

sub strip_java_comments {
    my ($text) = @_;
    my $out = "";
    my $state = "code";
    my $quote = "";
    my $i = 0;
    my $len = length($text);

    while ($i < $len) {
        my $char = substr($text, $i, 1);
        my $next = $i + 1 < $len ? substr($text, $i + 1, 1) : "";

        if ($state eq "code") {
            if ($char eq q{"} || $char eq q{'}) {
                $out .= $char;
                $quote = $char;
                $state = "string";
                $i++;
            } elsif ($char eq "/" && $next eq "/") {
                $out .= "  ";
                $state = "line_comment";
                $i += 2;
            } elsif ($char eq "/" && $next eq "*") {
                $out .= "  ";
                $state = "block_comment";
                $i += 2;
            } else {
                $out .= $char;
                $i++;
            }
        } elsif ($state eq "string") {
            $out .= $char;
            if ($char eq "\\") {
                if ($i + 1 < $len) {
                    $out .= substr($text, $i + 1, 1);
                    $i += 2;
                } else {
                    $i++;
                }
            } elsif ($char eq $quote) {
                $state = "code";
                $i++;
            } else {
                $i++;
            }
        } elsif ($state eq "line_comment") {
            if ($char eq "\n") {
                $out .= "\n";
                $state = "code";
            } else {
                $out .= " ";
            }
            $i++;
        } else {
            if ($char eq "*" && $next eq "/") {
                $out .= "  ";
                $state = "code";
                $i += 2;
            } else {
                $out .= $char eq "\n" ? "\n" : " ";
                $i++;
            }
        }
    }

    return $out;
}

sub matching_paren {
    my ($text, $open) = @_;
    my $depth = 0;
    my $quote = "";
    my $len = length($text);

    for (my $i = $open; $i < $len; $i++) {
        my $char = substr($text, $i, 1);
        if ($quote ne "") {
            if ($char eq "\\") {
                $i++;
            } elsif ($char eq $quote) {
                $quote = "";
            }
            next;
        }

        if ($char eq q{"} || $char eq q{'}) {
            $quote = $char;
        } elsif ($char eq "(") {
            $depth++;
        } elsif ($char eq ")") {
            $depth--;
            return $i if $depth == 0;
        }
    }

    return -1;
}

sub split_args {
    my ($content, $base_offset) = @_;
    my @args;
    my $depth = 0;
    my $quote = "";
    my $start = 0;
    my $len = length($content);

    for (my $i = 0; $i < $len; $i++) {
        my $char = substr($content, $i, 1);
        if ($quote ne "") {
            if ($char eq "\\") {
                $i++;
            } elsif ($char eq $quote) {
                $quote = "";
            }
            next;
        }

        if ($char eq q{"} || $char eq q{'}) {
            $quote = $char;
        } elsif ($char eq "(" || $char eq "[" || $char eq "{") {
            $depth++;
        } elsif ($char eq ")" || $char eq "]" || $char eq "}") {
            $depth-- if $depth > 0;
        } elsif ($char eq "," && $depth == 0) {
            push @args, [substr($content, $start, $i - $start), $base_offset + $start];
            $start = $i + 1;
        }
    }

    push @args, [substr($content, $start), $base_offset + $start];
    return @args;
}

sub first_string_literal {
    my ($arg, $offset) = @_;
    return if $arg !~ /\A\s*(["'])/;

    my $quote = $1;
    my $start = $+[0];
    my $literal_offset = $offset + $start - 1;
    my $literal = "";
    my $len = length($arg);

    for (my $i = $start; $i < $len; $i++) {
        my $char = substr($arg, $i, 1);
        if ($char eq "\\") {
            if ($i + 1 < $len) {
                $i++;
                $literal .= substr($arg, $i, 1);
            }
        } elsif ($char eq $quote) {
            return ($literal, $literal_offset);
        } else {
            $literal .= $char;
        }
    }

    return;
}

sub line_number {
    my ($text, $offset) = @_;
    return 1 + (substr($text, 0, $offset) =~ tr/\n//);
}

for my $file (@ARGV) {
    open my $fh, "<", $file or next;
    local $/;
    my $source = <$fh>;
    close $fh;

    my $code = strip_java_comments($source);
    while ($code =~ /(\bTags?\s*\.\s*of|\.\s*tags|\.\s*tag|\.\s*(?:counter|timer|summary|gauge))\s*\(/g) {
        my $call = $1;
        my $open = pos($code) - 1;
        my $close = matching_paren($code, $open);
        next if $close < 0;

        my $content = substr($code, $open + 1, $close - $open - 1);
        my @args = split_args($content, $open + 1);
        my @key_indexes;

        if ($call =~ /Tags\s*\.\s*of/ || $call =~ /\.tags/) {
            @key_indexes = grep { $_ % 2 == 0 } 0..$#args;
        } elsif ($call =~ /Tag\s*\.\s*of/ || $call =~ /\.tag/) {
            @key_indexes = (0);
        } else {
            @key_indexes = grep { $_ % 2 == 1 } 0..$#args;
        }

        for my $index (@key_indexes) {
            my ($literal, $literal_offset) = first_string_literal($args[$index][0], $args[$index][1]);
            next if !defined $literal;
            print join("\t", $file, line_number($source, $literal_offset), $literal, $call), "\n";
        }

        pos($code) = $open + 1;
    }
}
PERL
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
