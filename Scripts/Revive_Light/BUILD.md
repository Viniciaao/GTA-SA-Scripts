# Compilando o Jesus Enfermeiro (gta3script / gta3sc)

Este mod é escrito em **gta3script** e compilado com o
[`gta3sc`](https://github.com/thelink2012/gta3sc) — **não** com o Sanny Builder.

```bash
gta3sc "Jesus Enfermeiro.sc" --config=gtasa --guesser -fconst -farrays \
       -fbreak-continue -fifnot -flocal-var-limit=32 --cs \
       -o "Jesus Enfermeiro.cs"
```

O `.cs` e o `.ini` vão para a pasta `CLEO/` do jogo (ou pasta do ModLoader).

## Proper Shaders API

A integração com a API é **opcional em runtime**:

1. O script faz `GET_LOADED_LIBRARY "ProperShaders.asi"`.
2. Resolve `PS_LightCreate`, `PS_LightDestroy`, `PS_LightSetPosition`.
3. Monta um `PS_LightDesc` (88 bytes, layout do header oficial) e cria a luz.
4. Move com `PS_LightSetPosition` a cada quadro.

Documentação oficial:
- Header: https://github.com/MixMods/ProperShadersApiExample/blob/main/ProperShadersAPI.h
- Wiki: https://github.com/MixMods/ProperShadersApiExample/wiki
- Exemplo C++: https://github.com/MixMods/ProperShadersApiExample

Sem o ASI (ou com `UseProperShaders = 0`), o comportamento volta ao searchlight/corona.

## Notas de layout

| Bloco no `ALLOCATE_MEMORY` | Offset | Tamanho |
| --- | --- | --- |
| IDs de modelo | 0 | 128 (32 × 4) |
| Tabela de NPCs | 128 | 384 (16 × 24) |
| Buffer de linha INI | 512 | 256 |
| `PS_LightDesc` | 768 | 88 |
| Ponteiros/config PS | 856 | 40 |
| **Total** | | **896** |

Cada vaga da tabela (24 bytes): `ped | lightHandle | x | y | z | kind`
(`kind`: 0=none, 1=searchlight, 2=ProperShaders).
