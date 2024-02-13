build:
	helm dependency build

dry-run:
	helm install etcd ./ --debug --dry-run

install:
	helm install etcd ./

install-dev:
	kubectl create ns etcd
	helm install etcd --values ./values/dev.yaml ./ -n etcd

delete:
	helm uninstall etcd

list:
	helm list
