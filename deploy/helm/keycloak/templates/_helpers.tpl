{{- define "clinicflow-keycloak.labels" -}}
app.kubernetes.io/name: clinicflow-keycloak
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: clinicflow
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{- /* The Keycloak pods: the keycloakx chart labels them with this release, podLabels adds the component. */ -}}
{{- define "clinicflow-keycloak.podSelector" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: keycloak
{{- end }}
