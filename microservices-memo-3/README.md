## Docker

- dockerimageは**[Trusted Conten](https://hub.docker.com/search?q=)t**マークが付いているものを使用する
- trvyの[githubactions](https://github.com/aquasecurity/trivy-action)
- dockerfileのlintツール[hadolint](https://github.com/hadolint/hadolint?tab=readme-ov-file)  ciに組めばプルリク作成前にチェックできる [webページもある](https://hadolint.github.io/hadolint/)
- dockerの脆弱性診断ツール[dockle](https://github.com/goodwithtech/dockle)
- nodeのdockerfile作成のベストプラクティス
    - package.jsonのdevDependenciesは開発時のみのため本番のコンテナイメージには含めない
    - pnpm-lock.yamlはインストール時変更しないためフラグをつける必要がある
        - pnpm install —prod —frozen-lockfile
    - アプリケーションコードのみが変更された場合、パッケージの再インストールをさせない。
        - Lockファイルを先にコピーしパッケージの再インストールをしないことで、コンテナイメージ内のファイル変更量を抑えキャッシュを使えるようにし、ビルド時間短縮
        
        ```docker
        COPY .npmrc package.json pnpm-lock.yaml .pnpmfile.cjs ./
        pnpm install —prod —frozen-lockfile
        ```
        
    - イメージの作成やコンテナの起動に関係あいパッケージのインストールの際に生成されるlogファイルやnode_modulesはコピーする必要がないため、.dockerignoreしておく
- node.jsならprocess.envで環境変数を取得できるが、クライアント側で実行しているJavascriptからこれをそのまま参照できない。そのため、APP_ENVという環境変数を設定し、getInitalPropsを使って値をクライアント側に渡す必要がある。その上でk8sにenv name:APP_ENVのように指定して、クライアントに環境変数を渡す

## PodDisruptionBudgetによる安全なメンテナンス

- k8sを運用する中で、Podが稼働しているノードをメンテナンスしたい場合。その際はkubectl drainを用いて、ノード上のPodを退避させる必要がある。例えばノードが3台あって、レプリカ数2のアプリの場合で、1つのノードに pod2つ配置されている状況でその1つのノードをdrainしたらアプリが停止してしまう。
- 上記の状況を避けるために、`PodDisruptionBudget` を定義することで最低限必要なPod数を1と設定した場合、上記の状況でpod1つはdrainで退避されなくなる。

## PriorityClassによる起動優先順位の定義

- `PriorityClass`を設定することで、Podの起動優先度順位を定義できる。
- 優先度は`kubectl get priorityclass`、`kubectl describe pod`で確認できる
- Kubernetesのクラスター内で重要なコンポーネント(kube-apiserverやkube-controller-manager)がリソース不足時に追い出されないようにするために使う。
