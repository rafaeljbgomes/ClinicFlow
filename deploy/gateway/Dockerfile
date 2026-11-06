FROM traefik:v3.7.17@sha256:f4bb143d1135a579b630ed491e17ec50f6f7c9c6a30585ff3d385471d2a86f5a
COPY traefik.yaml /etc/traefik/traefik.yaml
COPY dynamic /etc/traefik/dynamic
USER 65532:65532
EXPOSE 8000 8443
CMD ["--configFile=/etc/traefik/traefik.yaml"]
