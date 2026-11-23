def call(Map config = [:]) {
    String appRepo     = config.appRepo
    String imageTag    = config.imageTag
    String dockerfile  = config.dockerfile ?: 'Dockerfile'
    String contextDir  = config.contextDir ?: '.'
    String sbomFile    = config.sbomFile ?: 'sbom.cdx.json'
    String archiveFile = config.archiveFile ?: 'image.tar'
    String failOn      = config.failOn ?: 'high'

    sh """
        set -euo pipefail

        export CONTAINERS_STORAGE_CONF=\$(mktemp)
        printf '[storage]\\ndriver = "vfs"\\nrunroot = "/tmp/containers-runroot"\\ngraphroot = "/tmp/containers-graphroot"\\n' > "\$CONTAINERS_STORAGE_CONF"
        trap 'rm -f "\$CONTAINERS_STORAGE_CONF"' EXIT

        podman build \\
            --isolation=chroot \\
            --network=host \\
            --layers \\
            -f "${dockerfile}" \\
            -t "${appRepo}:${imageTag}" \\
            -t "${appRepo}:latest" \\
            "${contextDir}"

        podman save --format oci-archive -o "${archiveFile}" "${appRepo}:${imageTag}"
        syft "oci-archive:${archiveFile}" -o cyclonedx-json > "${sbomFile}"
        grype "sbom:${sbomFile}" --fail-on ${failOn} --only-fixed
    """
}
