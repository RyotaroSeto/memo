# Redis Helmfile

## Using a password file

- redis-password という名前で パスワードを含むシークレットファイルを作成しなければならない

```bash
$ helmfile apply するとredisのパスワードが出力されるため、redis-password.txtに記載してsecretとして保管する
$ kubectl -n redis create secret generic redis-password-secret --from-file=redis-password.txt
```
