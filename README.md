# 📥 Scripts CLEO GTA SA


## ✨ O que tem aqui:


---

- **Academia da Groove Street Movimentada** — academia de Ganton viva: pedestres treinando, reação individual por pedestre, fumante que volta à calçada. (SanKing / reformulação CLEO 4.4.4+ / CLEO+)

- **Revive_Light** — "Jesus Enfermeiro": Luz em cima de algum NPC por meio de ini, com integração opcional à **API do Proper Shaders** (luz per-pixel deferred; fallback searchlight/corona). (Junior_Djjr / reescrita CLEO+)

- **NFRShift Gears Soundize** — câmbio manual (recriação do NFRShift Gears Mod) integrado ao mod **Soundize** (RPM/marcha reais no HUD via API oficial, controle de marcha sem brigar com o som) e com opção `DisableShiftAnim` pra compatibilidade com o **VEHIK** do zzpuma. Requer CLEO+ (e Soundize, opcional).

- **SanNews TV Programs** — overlay de jornalismo (San News / Cidade Alerta) na camera cinematica do veiculo, estilo Weazel News do GTA V. Reescrita em **gta3script** do script Sanny do RenanMSV que nunca ligava: sprites 6500 estouravam o array, nao tinha `.fxt`, o texto ia atras da faixa, o clima era lido com 4 bytes e o ticker voava em 60 FPS. Source em [`Scripts/SanNews TV Programs`](Scripts/SanNews%20TV%20Programs) (compilado com o `gta3sc`).

- **Som de pastilha de freio gasta** — chiado de pastilha gasta 3D ao frear, agora **também nos carros dos NPCs** (v2.7, reescrita em **gta3script** com o `gta3sc`): o som sai de qualquer carro do jogo dentro do raio, o valor que define "carro velho" é lido da própria handling (`GET_CAR_VALUE` do CLEO+, sem lista no `.ini`), o volume escala com o pedal e a velocidade e segue o menu de áudio, o chiado se repete enquanto o freio estiver apertado (`Cooldown`), a cantiga tem velocidade mínima (`MinSpeed`) e tudo é independente de FPS. A v2.7 conserta o bug que deixava a v2.6 **completamente muda** (a conversão `CSET` com os operandos invertidos zerava a pressão de frenagem) e ganha um teste que trava a ordem dos operandos. Fonte, `.cs` compilado, `.ini` comentado e receita de build em [`Scripts/Som de pastilha de freio gasta`](Scripts/Som%20de%20pastilha%20de%20freio%20gasta) (detalhes do que mudou em [`MELHORIAS.md`](Scripts/Som%20de%20pastilha%20de%20freio%20gasta/MELHORIAS.md)).

- **Visual Path Editor** — editor de paths (nodes/navis/links) do GTA SA in-game. **Correção do salvamento** (v1.1): o arquivo salvo saía incompleto (faltava a seção de interseções), os nodes afundavam 0,25 m a cada save e links inválidos iam pro disco sem aviso — agora valida tudo antes de gravar, grava o layout completo do jogo, faz backup `.bak` e recusa arquivo inválido. O `VisualPathEditor.cs` já compilado está na pasta do mod (source em **gta3script**, compilado com o `gta3sc` — receita em [`BUILD.md`](Scripts/Visual%20Path%20Editor/BUILD.md)). Detalhes das correções em [`CORRECOES.md`](Scripts/Visual%20Path%20Editor/CORRECOES.md) e referência do formato em [`PATHS-FORMAT.md`](Scripts/Visual%20Path%20Editor/PATHS-FORMAT.md).

---
