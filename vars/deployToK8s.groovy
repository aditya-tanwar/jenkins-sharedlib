def call(Map config = [:]) {
    String namespace    = config.namespace
    String manifestPath = config.manifestPath
    String imageRef     = config.imageRef
    String deployment   = config.deployment

    sh """
        set -euo pipefail
        kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f -
        sed "s|PLACEHOLDER_IMAGE_REF|${imageRef}|g" "${manifestPath}" > rendered-manifest.yaml
        kubectl apply -f rendered-manifest.yaml
        kubectl rollout status deployment/${deployment} -n "${namespace}" --timeout=120s
        kubectl get pods,svc -n "${namespace}"
    """
}
