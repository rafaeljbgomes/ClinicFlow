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
