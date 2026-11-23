package com.devops

import groovy.json.JsonOutput

class ProvenanceBuilder implements Serializable {
    static String generateSlsaV1(Map params) {
        def payload = [
            buildDefinition: [
                buildType: "https://jenkins.io",
                externalParameters: [
                    repository: params.gitUrl,
                    commit: params.gitCommitSha,
                    dockerfile: params.dockerfile
                ],
                internalParameters: [
                    jenkinsJob: params.jobName,
                    buildNumber: params.buildNumber,
                    nodeName: params.nodeName
                ],
                resolvedDependencies: [
                    [
                        uri: params.gitUrl,
                        digest: [sha1: params.gitCommitSha]
                    ]
                ]
            ],
            runDetails: [
                builder: [
                    id: params.jenkinsUrl ?: "jenkins-in-cluster"
                ],
                metadata: [
                    invocationId: params.buildTag
                ]
            ]
        ]
        return JsonOutput.prettyPrint(JsonOutput.toJson(payload))
    }
}
