FROM traefik:v3.7.15@sha256:0f6150202bc1fed72a7bd2107460eaa510e5b8a905f3fdb5b84f2c8619c33368
COPY traefik.yaml /etc/traefik/traefik.yaml
COPY dynamic /etc/traefik/dynamic
USER 65532:65532
EXPOSE 8000 8443
CMD ["--configFile=/etc/traefik/traefik.yaml"]
