{{- define "clinicflow-platform.labels" -}}
app.kubernetes.io/name: clinicflow-platform
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: clinicflow
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "clinicflow-platform.postgresSecretName" -}}
{{- default (printf "%s-postgres" .Release.Name) .Values.postgres.existingSecret -}}
{{- end }}

{{- define "clinicflow-platform.rabbitmqSecretName" -}}
{{- default (printf "%s-rabbitmq" .Release.Name) .Values.rabbitmq.existingSecret -}}
{{- end }}

{{- define "clinicflow-platform.bootstrapConfigName" -}}
{{- printf "%s-bootstrap" .Release.Name -}}
{{- end }}

{{- /* Service names from files/bootstrap/service-identities.conf, one per line (ADR-026). */ -}}
{{- define "clinicflow-platform.bootstrapServices" -}}
{{- range .Files.Lines "files/bootstrap/service-identities.conf" }}
{{- $line := trim . }}
{{- if and $line (not (hasPrefix "#" $line)) }}
{{ regexFind `^\S+` $line }}
{{- end }}
{{- end }}
{{- end }}

{{- define "clinicflow-platform.bootstrapMounts" -}}
- name: bootstrap
  mountPath: /platform-bootstrap
  readOnly: true
{{- range splitList "\n" (include "clinicflow-platform.bootstrapServices" . | trim) }}
- name: {{ . }}
  mountPath: /credentials/{{ . }}
  readOnly: true
{{- end }}
{{- end }}
