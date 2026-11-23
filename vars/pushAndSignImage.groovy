import com.devops.ProvenanceBuilder

def call(Map config = [:]) {
    String appRepo        = config.appRepo
    String imageTag       = config.imageTag
    String dockerCreds    = config.dockerCreds ?: 'dockerhub-creds'
    String cosignKeyCred  = config.cosignKeyCred ?: 'cosign-private-key'
    String cosignPassCred = config.cosignPassCred ?: 'cosign-password'
    String sbomFile       = config.sbomFile ?: 'sbom.cdx.json'
    String slsaFile       = config.slsaFile ?: 'slsa-provenance.json'
    String digestFile     = 'image-digest.txt'

    withCredentials([
        usernamePassword(credentialsId: dockerCreds, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_TOKEN'),
        file(credentialsId: cosignKeyCred, variable: 'COSIGN_KEY'),
        string(credentialsId: cosignPassCred, variable: 'COSIGN_PASSWORD')
    ]) {
        sh """
            set -euo pipefail
            set +x

            export CONTAINERS_STORAGE_CONF=\$(mktemp)
            printf '[storage]\\ndriver = "vfs"\\nrunroot = "/tmp/containers-runroot"\\ngraphroot = "/tmp/containers-graphroot"\\n' > "\$CONTAINERS_STORAGE_CONF"
            export REGISTRY_AUTH_FILE="${env.WORKSPACE}/.docker/config.json"
            export DOCKER_CONFIG="\$(dirname "\$REGISTRY_AUTH_FILE")"
            trap 'rm -f "\$CONTAINERS_STORAGE_CONF"' EXIT

            mkdir -p "\$DOCKER_CONFIG"
            printf '{}' > "\$REGISTRY_AUTH_FILE"

            printf '%s' "\$DOCKER_TOKEN" | podman login --username "\$DOCKER_USER" --password-stdin docker.io
            printf '%s' "\$DOCKER_TOKEN" | cosign login --username "\$DOCKER_USER" --password-stdin docker.io

            podman push --digestfile="${digestFile}" "${appRepo}:${imageTag}"
            podman push "${appRepo}:latest"
        """

        def digest = readFile(digestFile).trim()
        if (!digest.startsWith('sha256:')) {
            error "Invalid digest captured: ${digest}"
        }
        String imageRef = "${appRepo}@${digest}"

        // Generate SLSA JSON using our src/ Groovy class
        String slsaJson = ProvenanceBuilder.generateSlsaV1([
            gitUrl: config.gitUrl,
            gitCommitSha: config.gitCommitSha,
            dockerfile: config.dockerfile ?: 'Dockerfile',
            jobName: env.JOB_NAME,
            buildNumber: env.BUILD_NUMBER,
            nodeName: env.NODE_NAME,
            jenkinsUrl: env.JENKINS_URL,
            buildTag: env.BUILD_TAG
        ])
        writeFile file: slsaFile, text: slsaJson

        sh """
            set -euo pipefail
            export DOCKER_CONFIG="${env.WORKSPACE}/.docker"

            cosign sign --yes --key "\$COSIGN_KEY" "${imageRef}"
            cosign attest --yes --key "\$COSIGN_KEY" --predicate "${sbomFile}" --type cyclonedx "${imageRef}"
            cosign attest --yes --key "\$COSIGN_KEY" --predicate "${slsaFile}" --type slsaprovenance1 "${imageRef}"
        """

        return imageRef
    }
}
