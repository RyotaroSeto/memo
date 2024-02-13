# 検証環境構築

## 1.Multipass をインストール
```bash
$ brew install multipass
```

## 2.Intel Mac 用セットアップスクリプトをダウンロードしてホーム直下に展開
[こちら](https://www.sbcr.jp/support/4815617794/)からセットアップスクリプトtinet_mac.zip をダウンロードし，ホームディレクトリ直下に tinet ディレクトリとして配置。

## 3.Multipass で Ubuntu 20.04 の仮想マシンを作成
```bash
$ multipass launch 20.04 --cpus 2 --name UBUNTU --mount $HOME/tinet:/mnt/c/tinet
```

## 4.Multipass のシェルに入る
```bash
$ multipass shell UBUNTU
```

## 5.仮想マシン内の root ユーザのパスワードを設定してrootに入る
```bash
ubuntu@UBUNTU:~$ sudo passwd root
New password:〈パスワード入力〉
Retype new password:〈パスワード再入力〉
passwd: password updated successfully
ubuntu@UBUNTU:~$ su
Password:〈パスワード入力〉
root@UBUNTU:/home/ubuntu#
```

## 6.仮想マシン内でセットアップスクリプトを実行
```bash
root@UBUNTU:/home/ubuntu# bash /mnt/c/tinet/setup_mac.sh
```

## 7.チェックスクリプトで確認
```bash
root@UBUNTU:/home/ubuntu# bash /mnt/c/tinet/check_mac.sh
```

## 8.検証環境の構築
```bash
root@UBUNTU:/home/ubuntu# tinet up -c /mnt/c/tinet/spec_01.yaml | sh -x
root@UBUNTU:/home/ubuntu# tinet conf -c /mnt/c/tinet/spec_01.yaml | sh -x
```

## 9.検証環境の動作確認
```bash
root@UBUNTU:/home/ubuntu# docker exec -it cl1 /bin/bash
root@cl1:/#
```
