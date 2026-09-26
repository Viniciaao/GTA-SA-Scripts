# Compilando o SanNews TV Programs (gta3script / gta3sc)

Este mod e escrito em **gta3script** e compilado com o
[`gta3sc`](https://github.com/thelink2012/gta3sc) — **nao** com o Sanny Builder.

```bash
gta3sc compile --config=gtasa --guesser --cs -fno-entity-tracking -O \
    sannews.sc -o sannews.cs
```

O `.cs` vai para `CLEO/` junto com o `sannews.fxt`. A TXD original
(`models/txd/sannews.txd`) e opcional: sem ela a overlay ainda desenha
letterbox, faixa e textos.
