`curl -LO https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.39.0/strimzi-0.39.0.zip`
`unzip strimzi-0.39.0.zip`
`sed -i '' 's/namespace: .*/namespace: kafka/' install/cluster-operator/*RoleBinding*.yaml`
