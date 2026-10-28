# R2 e Firebase push

- Conta Cloudflare: c76785cfc7b17e3afc1e7fbf3bda51d7.
- Bucket privado Standard: mercado-facil-fotos. Acesso público desativado.
- Worker: https://mercado-facil-api.brandtgustavo2001.workers.dev.
- Firebase Spark: mercado-facil-9abe5; Android br.com.mercadofacil registrado.
- Migração 202610280003_r2_and_push.sql aplicada ao Supabase em 28/10/2026.
- Worker implantado pelo editor do painel, com fonte compactada equivalente ao worker.mjs. Uma próxima implantação via Wrangler deve usar este arquivo e preservar os segredos.

## Pendente

Aguardar confirmação do titular para criar uma conta de serviço exclusiva com a permissão roles/firebasecloudmessaging.admin (envio de mensagens), gerar sua chave JSON e guardá-la como segredo FIREBASE_SERVICE_ACCOUNT no Worker. Nunca versionar essa chave, colocá-la no APK ou imprimir seus valores.
O endpoint /health confirma r2=true e push=false enquanto o segredo estiver ausente. Nenhum APK novo foi publicado nesta etapa.

## Fluxos

O app usa o JWT de sessão do Supabase no Worker. O Worker verifica o usuário, o papel da lista e o produto antes de acessar o R2. Fotografias são objetos JPEG imutáveis identificados por SHA-256. Postgres guarda só object_key/content_hash; set_r2_photo limpa o conteúdo legado somente após o upload R2. O app migra fotos legadas quando um editor abre e sincroniza a lista; visualizadores continuam com acesso pela API autenticada. Até todos atualizarem, fotos antigas ainda podem ocupar Postgres. Objetos antigos no R2 são preservados para evitar corrida entre substituições.

Gatilhos do banco geram eventos para itens/compras. Cada evento tem uma capability aleatória de 256 bits e validade de um dia, mantida privada. O Worker recebe essa capability por HTTPS e obtém somente os tokens dos participantes atuais (exceto o autor), com notificações habilitadas. Registra confirmações por instalação. Cron repete tentativas malsucedidas, limitado a 15 tentativas; eventos expiram. Não há chave administrativa do Supabase no Worker. As notificações usam texto genérico para evitar expor o conteúdo da lista na tela bloqueada.

O aplicativo mantém a fila offline e copia a imagem no banco local. As notificações periódicas anteriores continuam como fallback até o dispositivo registrar FCM. O primeiro carregamento não notifica histórico antigo. Sair da conta remove o token no servidor antes de limpar a sessão.

## Validação desta etapa

- Compilação Android, 56 testes unitários/integrados locais e lint aprovados.
- node --test cloudflare/worker.test.mjs: 7 casos de acesso, upload e isolamento.
- SQL no Supabase em transação com rollback: registro/saída do dispositivo, bloqueio de viewer, seleção de destinatários e deduplicação passaram.
- Endpoint público /health respondeu e /photos sem login retornou 401.
- Ainda não validado: foto real no R2, envio FCM real, dois aparelhos e app encerrado. Não afirmar push ativo antes de configurar a credencial e testar.
