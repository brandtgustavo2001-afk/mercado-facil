# Mercado Fácil

Aplicativo Android nativo de listas de compras, em português brasileiro. Kotlin, Jetpack Compose Material 3, MVVM, Room, DataStore, OkHttp e WorkManager. Android 8/API 26 ou superior.

**Entrega incremental em desenvolvimento.** O APK e os testes locais não certificam sincronização em múltiplos aparelhos nem o fluxo completo de atualização. Consulte [STATUS.md](STATUS.md) para as limitações e validações pendentes.

## Abrir e compilar

Abra esta pasta no Android Studio. Use JDK 17, SDK `platforms;android-36-ext19` e Build Tools `36.0.0`. O wrapper baixa Gradle 8.13. As versões estão fixadas para o SDK disponível; migrar ao SDK estável mais recente ainda é uma pendência.

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
./gradlew.bat assembleDebugAndroidTest
./gradlew.bat connectedDebugAndroidTest # exige celular/emulador conectado
```

APK de teste: `app/build/outputs/apk/debug/app-debug.apk`. O Android exige autorização de instalação dessa origem. A assinatura debug é somente para desenvolvimento: exporte um backup antes de trocar de uma instalação debug para uma release assinada com outra chave.

## Funcionalidades locais

A tela inicial é uma lista direta: digite o produto no campo inferior e toque em **+**. O que falta comprar fica na seção **Para comprar**, no topo. Ao marcar um produto, ele passa para **Selecionados**, abaixo; toque outra vez para desfazer a seleção. Depois de pagar, use o botão fixo **Comprados** na parte inferior para transferir apenas os produtos selecionados ao **Histórico**. Itens ainda não selecionados continuam na lista. A aba de histórico guarda as **10 compras mais recentes** de todas as listas; registros mais antigos são automaticamente excluídos. O histórico é armazenado **neste aparelho** e incluído no backup JSON, mas não é sincronizado entre celulares pelo Supabase. Edição, exclusão, compartilhamento e configurações continuam disponíveis nos menus. A identidade visual está documentada em [branding/IDENTIDADE.md](branding/IDENTIDADE.md).

Crie listas e produtos sem login e sem conexão. Preços são centavos inteiros; quantidades são decimais exatos, com até três casas. Cada linha é arredondada uma vez em HALF_UP. Há orçamento e projeção, marcação de compras, edição de preço e quantidade, duplicação, arquivo, exclusão confirmada, categorias, busca, sugestões de produtos anteriores, favoritos, histórico de listas, resumo por mês/categoria e temas claro/escuro/automático.

Compartilhamento por texto funciona sem Supabase. Backups JSON e relatórios CSV usam o seletor de arquivos Android. Importar exige confirmação, é transacional, preserva os UUIDs e acrescenta registros ausentes sem substituir dados existentes. Listas importadas são locais. O backup JSON versão 2 cobre listas, itens e as últimas 10 compras; restauração também aceita backups anteriores de versão 1. Configurações e sessões não são exportadas.

Leitura de códigos usa Google Code Scanner: requer Google Play Services e o módulo de leitura disponível. Consulta opcional ao Open Food Facts por HTTPS; indisponibilidade ou produto ausente permite cadastro manual. A disponibilidade dessa base não é garantida.

Lembretes usam WorkManager e podem sofrer atraso pelo gerenciamento de bateria do Android. Notificações dependem da permissão do sistema no Android 13+.

## Supabase

O APK já inclui a URL e a chave **publishable** do projeto `igehbjnutmhkqfdlrndx`. O esquema foi aplicado e os testes SQL de RPCs/RLS passaram. Para usar, abra **menu (...) → Conta e ajustes → Conta e sincronização**; escolha **Criar conta**, informe nome, e-mail e senha e confirme o e-mail se for solicitado. Depois, entre usando a mesma conta. A conta usada para administrar o painel Supabase pelo GitHub é separada das contas dos usuários do aplicativo. A partir da versão 1.0.6, o aplicativo mostra mensagens mais claras para e-mail não confirmado, senha errada e limitação de cadastros pelo servidor. Se o servidor desativar a inscrição por e-mail ou bloquear o envio por limite de mensagens, isso exige ajuste no Supabase e não pode ser resolvido apenas pelo APK.

Não execute a migração inicial novamente nesse projeto. Os passos abaixo são para configurar outro projeto novo.

1. Crie um projeto Supabase e execute `supabase/migrations/001_initial.sql` no SQL Editor **em um projeto novo**.
2. Habilite e-mail/senha em Auth e configure SMTP, confirmação de e-mail e a página de recuperação no painel Supabase. O aplicativo solicita o e-mail de recuperação; a troca de senha ocorre no fluxo web configurado no Supabase.
3. Em Ajustes no app, informe a URL HTTPS e a chave pública `anon` ou `publishable`. **Nunca use `service_role`, senha de banco ou `sb_secret_`.**
4. Cadastre/entre em uma conta. Abra a lista desejada e use menu → Compartilhar lista → Criar convite para esta lista. O aplicativo ativa a colaboração e envia os produtos existentes antes de gerar o convite.
5. Envie o convite ao outro participante. No outro aparelho, entre na conta, abra Compartilhar lista, cole o código ou a mensagem em Convite recebido e toque em Abrir lista compartilhada. A lista recebida será aberta automaticamente. O convite tem 256 bits, validade de 24 horas e um uso; no servidor só fica seu SHA-256.
6. Proprietários podem revogar convites e remover participantes; editores escrevem e visualizadores só leem. As RPCs validam as permissões independentemente do cliente.

A UI lê apenas Room. Gravações locais e a fila são transacionais. RPCs usam UUID idempotente, lock por lista, comparação dos campos com os valores de base e auditoria no servidor. Alterações independentes mesclam; campos divergentes ficam em conflito e exigem escolha explícita em Ajustes. Exclusões são tombstones retidos indefinidamente nesta versão, evitando ressurreição por aparelhos antigos.

Em um projeto Supabase de testes, rode `supabase/tests/sync_acceptance.sql` após a migração para testar as RPCs e as políticas RLS. O script insere usuários temporários e faz rollback ao terminar. Os testes passaram no projeto configurado, e a ausência de registros temporários foi verificada.

Realtime sinaliza alterações e o motor busca os registros autorizados, em páginas, atualizando Room. Há reconexão com backoff e refresh da sessão. A fila também roda pelo WorkManager. O cache compartilhado fica disponível offline; revogação é aplicada quando o cliente consegue consultar o servidor. Acesso remoto é bloqueado imediatamente pelas políticas. A implementação atual reconsulta snapshots e ainda não usa cursor incremental durável: veja STATUS.

O SQL usa JSONB para os campos mutáveis e colunas relacionais para identidade, autorização e versão. Isso permite comparação por campo na RPC, mas difere das colunas individuais propostas no briefing. Não aplique a migração cegamente a um esquema já existente.

## Atualizações por GitHub Releases

Repositório padrão: `brandtgustavo2001-afk/mercado-facil`, configurável em Ajustes. Não há token GitHub no APK.

Uma release estável `v1.0.1` deve conter exatamente:

```text
mercado-facil-1.0.1.apk
mercado-facil-1.0.1.apk.sha256
```

O `.sha256` usa o formato do `sha256sum`. O aplicativo consulta `/releases/latest`, compara SemVer numericamente, mostra as notas, baixa com progresso e cancelamento, valida SHA-256, applicationId, assinatura e versionCode, e abre uma sessão PackageInstaller. A autorização de origem desconhecida e a confirmação do Android continuam obrigatórias. Depois de autorizar a origem, volte ao app e toque em Instalar novamente.

A partir da 1.0.2, o aviso automático oferece **Atualizar agora** e continua a instalação após a autorização de origem. A verificação usa um intervalo de 12 horas por padrão; a consulta manual em Ajustes ignora o intervalo. Atualizações já encontradas ficam em cache durante esse período. Na 1.0.1, use Ajustes → Verificar atualizações para obter a 1.0.2; o fluxo antigo pede baixar e depois instalar.

As versões do canal automático precisam ser releases normais: GitHub não inclui pré-releases em `/releases/latest`. O workflow cria um rascunho, envia APK/checksum e então publica a release normal. Não remova esses arquivos de uma versão já publicada.

A assinatura precisa ser **a mesma em todas as versões**. Proteção criptográfica final vem da chave de assinatura Android; checksum e APK na mesma release não substituem essa verificação. Esta versão aceita assinantes idênticos, sem rotação de chave.

O banco não usa migração destrutiva. Ao mudar o esquema, incremente a versão Room, adicione uma `Migration` explícita e mantenha os JSONs em `app/schemas`. Antes de publicar, teste a atualização com listas, sessão e fila pendente no aparelho.

## Assinatura e release

Para criar a primeira chave local e compilar um release:

```powershell
# Defina JAVA_HOME para seu JDK 17
python tools/build_release.py
```

A chave e as senhas ficam **fora do repositório**, em `~/.codex/secrets/mercado-facil`. Faça backup seguro dessa pasta; perder a chave impede atualizar instalações existentes. O script não imprime senhas e reutiliza a chave nas próximas execuções. O arquivo `tools/build_release.py` gera a versão 1.0.12 por padrão; use `--version` e `--code` para selecionar outra versão.

Configure os quatro Secrets no GitHub → Settings → Secrets and variables → Actions:

| Secret | Conteúdo |
|---|---|
| `KEYSTORE_BASE64` | Keystore codificado em Base64 |
| `SIGNING_STORE_PASSWORD` | Senha do keystore |
| `SIGNING_KEY_ALIAS` | Alias da chave |
| `SIGNING_KEY_PASSWORD` | Senha da chave |

Neste repositório, os quatro Secrets já foram configurados com a chave local da entrega inicial. O utilitário `tools/configure_github_signing.py` permite repetir a configuração usando uma sessão autorizada do Git Credential Manager e PyNaCl; ele cifra os valores com a chave pública do GitHub e não exibe credenciais.

Para compilação manual, forneça `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` por variáveis de ambiente, sem colocá-las no Git.

```powershell
./gradlew.bat assembleRelease -PversionName=1.0.1 -PversionCode=10001
git tag v1.0.1
git push origin v1.0.1
```

O workflow verifica testes/lint/debug, exige credenciais, assina a release, calcula checksum e publica assets e notas. O versionCode é `major*10000 + minor*100 + patch`; minor e patch devem ficar entre 0 e 99. Cada versão precisa aumentar o código. Ausência de Secret falha de forma segura. Nunca envie keystore, JSON de credenciais ou senhas para o repositório público.

## Fontes técnicas

- [Configuração e compatibilidade AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Room e dependências](https://developer.android.com/jetpack/androidx/releases/room)
- [Supabase Realtime Protocol](https://supabase.com/docs/guides/realtime/protocol)
- [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)

