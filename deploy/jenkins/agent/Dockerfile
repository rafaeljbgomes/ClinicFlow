FROM jenkins/ssh-agent:8.9.0-jdk21@sha256:32cbd440e4d665a2cbe77b9f79f948156dbd606447f400940300e115b9d35cb6

USER root

ARG NODE_VERSION=24.19.0
ARG NODE_SHA256=14b342e71204f811bde6153be8e04b62aef63c236fef92b55f9c83154b409647
ARG POWERSHELL_VERSION=7.6.4
ARG POWERSHELL_SHA256=4471b5a36bfe86ec7af8525d36bb1cacba0128e7aac22d05cc064bc00e604721
ARG DOCKER_VERSION=29.6.2
ARG DOCKER_SHA256=d6204aea92238e2453d5445c885b9d2e5eb8f82915568ec50edf9dbe12a3ac74
ARG COMPOSE_VERSION=5.4.0
ARG COMPOSE_SHA256=837fd1d35bf6a494f41b5b5988269a7be79de337cf1a1a6ff0e45ab51bb4e9be
ARG HELM_VERSION=4.2.4
ARG HELM_SHA256=c306b46f719b0a4da32d0f78ee21bf90ce8d602f15b22ab753f0674d1670a7f3
ARG KUBECONFORM_VERSION=0.8.0
ARG KUBECONFORM_SHA256=9bc2bffbf71f261128533edaf912153948b7ff238f9a531ae6d34466ec287883
ARG ACTIONLINT_VERSION=1.7.12
ARG ACTIONLINT_SHA256=8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8
ARG BETTERLEAKS_VERSION=1.8.1
ARG BETTERLEAKS_SHA256=efa407244e1ea8e35f582b8a42becdeac08bdead04f68eb752adda722d583c2a
ARG PLAYWRIGHT_VERSION=1.62.1

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl git git-lfs gnupg jq openssl procps tar unzip xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
       -o /tmp/node-linux-x64.tar.xz \
    && echo "${NODE_SHA256}  /tmp/node-linux-x64.tar.xz" | sha256sum --check --strict \
    && tar -xJf /tmp/node-linux-x64.tar.xz -C /usr/local --strip-components=1 \
    && rm /tmp/node-linux-x64.tar.xz \
    && node --version \
    && npm --version

RUN mkdir -p /opt/microsoft/powershell/7 \
    && curl -fsSL "https://github.com/PowerShell/PowerShell/releases/download/v${POWERSHELL_VERSION}/powershell-${POWERSHELL_VERSION}-linux-x64.tar.gz" \
       -o /tmp/powershell-linux-x64.tar.gz \
    && echo "${POWERSHELL_SHA256}  /tmp/powershell-linux-x64.tar.gz" | sha256sum --check --strict \
    && tar -xzf /tmp/powershell-linux-x64.tar.gz -C /opt/microsoft/powershell/7 \
    && rm /tmp/powershell-linux-x64.tar.gz \
    && chmod +x /opt/microsoft/powershell/7/pwsh \
    && ln -s /opt/microsoft/powershell/7/pwsh /usr/local/bin/pwsh

RUN curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz" \
       -o /tmp/docker-linux-x86_64.tgz \
    && echo "${DOCKER_SHA256}  /tmp/docker-linux-x86_64.tgz" | sha256sum --check --strict \
    && tar -xzf /tmp/docker-linux-x86_64.tgz --strip-components=1 -C /usr/local/bin docker/docker \
    && rm /tmp/docker-linux-x86_64.tgz \
    && mkdir -p /usr/local/lib/docker/cli-plugins \
    && curl -fsSL "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
       -o /usr/local/lib/docker/cli-plugins/docker-compose \
    && echo "${COMPOSE_SHA256}  /usr/local/lib/docker/cli-plugins/docker-compose" | sha256sum --check --strict \
    && chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

RUN curl -fsSL "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" \
       -o /tmp/helm-linux-amd64.tar.gz \
    && echo "${HELM_SHA256}  /tmp/helm-linux-amd64.tar.gz" | sha256sum --check --strict \
    && tar -xzf /tmp/helm-linux-amd64.tar.gz --strip-components=1 -C /usr/local/bin linux-amd64/helm \
    && rm /tmp/helm-linux-amd64.tar.gz \
    && helm version --short \
    && curl -fsSL "https://github.com/yannh/kubeconform/releases/download/v${KUBECONFORM_VERSION}/kubeconform-linux-amd64.tar.gz" \
       -o /tmp/kubeconform-linux-amd64.tar.gz \
    && echo "${KUBECONFORM_SHA256}  /tmp/kubeconform-linux-amd64.tar.gz" | sha256sum --check --strict \
    && tar -xzf /tmp/kubeconform-linux-amd64.tar.gz -C /usr/local/bin kubeconform \
    && rm /tmp/kubeconform-linux-amd64.tar.gz \
    && curl -fsSL "https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/actionlint_${ACTIONLINT_VERSION}_linux_amd64.tar.gz" \
       -o /tmp/actionlint-linux-amd64.tar.gz \
    && echo "${ACTIONLINT_SHA256}  /tmp/actionlint-linux-amd64.tar.gz" | sha256sum --check --strict \
    && tar -xzf /tmp/actionlint-linux-amd64.tar.gz -C /usr/local/bin actionlint \
    && rm /tmp/actionlint-linux-amd64.tar.gz \
    && curl -fsSL "https://github.com/betterleaks/betterleaks/releases/download/v${BETTERLEAKS_VERSION}/betterleaks_${BETTERLEAKS_VERSION}_linux_x64.tar.gz" \
       -o /tmp/betterleaks-linux-x64.tar.gz \
    && echo "${BETTERLEAKS_SHA256}  /tmp/betterleaks-linux-x64.tar.gz" | sha256sum --check --strict \
    && tar -xzf /tmp/betterleaks-linux-x64.tar.gz -C /usr/local/bin betterleaks \
    && rm /tmp/betterleaks-linux-x64.tar.gz

RUN npx --yes "playwright@${PLAYWRIGHT_VERSION}" install --with-deps chromium \
    && chmod -R a+rX /ms-playwright \
    && npm cache clean --force

RUN apt-get update \
    && apt-get install --no-install-recommends -y libicu76 \
    && rm -rf /var/lib/apt/lists/*

COPY clinicflow-entrypoint.sh /usr/local/bin/clinicflow-entrypoint
RUN chmod +x /usr/local/bin/clinicflow-entrypoint \
    && mkdir -p /home/jenkins/agent /home/jenkins/.m2 /home/jenkins/.npm \
    && git config --system --add safe.directory /workspace/clinicflow \
    && git config --system --add safe.directory /workspace/clinicflow/.git \
    && chown -R jenkins:jenkins /home/jenkins /ms-playwright

ENTRYPOINT ["clinicflow-entrypoint"]
