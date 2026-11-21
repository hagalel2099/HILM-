# Atualizador do Nosso Universo — Cloudflare Worker

Este Worker consulta a última release do repositório privado `hagalel2099/HILM-`
e entrega o APK por uma rota intermediária. O token do GitHub fica somente como
Secret no Cloudflare; nunca deve ser colocado no aplicativo Android.

## Arquivos
- `src/index.js`: serviço.
- `wrangler.toml`: configuração do Worker.
- `package.json`: comando de implantação.

## Secret obrigatório
No Worker, crie um Secret com:
- Nome: `GITHUB_TOKEN`
- Valor: token fine-grained do GitHub com acesso somente de leitura ao conteúdo
  do repositório `HILM-` (permissão Contents: Read-only).

## Rotas
- `GET /`: teste simples.
- `GET /repos/hagalel2099/HILM-/releases/latest`: dados da última release; os
  links dos APKs apontam para `/download/{assetId}` neste Worker.
- `GET /download/{assetId}`: entrega um APK pertencente à última release.

## Importante
O código Android que consulta a release deve usar:
`https://SEU-WORKER.workers.dev/repos/hagalel2099/HILM-/releases/latest`

O link de download devolvido no JSON já aponta para o Worker. O repositório
permanece privado. Não publique nem compartilhe o token.
