# AEDS-3-TPs — Banco de Dados Binário de Livros (TP01 + TP02 + TP03)

## Visão Geral do Projeto

Este projeto implementa um sistema de banco de dados de livros em arquivo binário usando Java, com **índice em Árvore B** armazenado em disco. Sobre essa base (TP02), o TP03 acrescenta **compactação (Huffman e LZW)** e **casamento de padrões (KMP e Boyer-Moore)**.

O projeto é composto pelos seguintes componentes:

**TP02 (base):**
1. **Conversor CSV para Binário** (`GeradorBin.java`) — Lê dados de livros de um arquivo CSV, converte para um formato binário personalizado e constrói o índice em Árvore B.
2. **Árvore B em Disco** (`ArvoreBMais.java`) — Implementa uma Árvore B parametrizável em ordem, mapeando `id → posição no arquivo binário`, com inserção, busca, atualização de endereço e remoção com redistribuição/merge.
3. **Gerenciador de Arquivo Binário** (`Menu.java`) — Menu interativo para o usuário escolher como alterar os dados.
4. **Manipulador de Arquivo Binário** (`CRUD.java`) — Operações CRUD (Create, Read, Update, Delete) usando o índice da Árvore B para localização direta de registros.
5. **Ordenador de Arquivo Binário** (`Ordenacao.java`) — Ordena o arquivo binário, deletando fisicamente registros logicamente deletados, com atualização em massa das posições na Árvore B.
6. **Lista Invertida** (`ListaInvertida.java`) — Índice invertido para busca textual.

**TP03 (acréscimos):**
7. **Compactação** (`Huffman.java`, `LZW.java`, `Compressao.java`) — Dois algoritmos de compressão byte a byte com versionamento, relatório de tempos e validação por checksum.
8. **Casamento de Padrões** (`KMP.java`, `BoyerMoore.java`, `CasamentoPadroes.java`) — Busca de padrões nos campos de texto com dois algoritmos complementares.
9. **Reindexação** (`Reindexacao.java`) — Reconstrói a Árvore B e as listas invertidas após restauração de uma versão antiga da base.
10. **Testes** (`TesteTP3.java`) — 46 verificações automatizadas cobrindo compressão, casamento de padrões, versionamento e regressão do TP02.

---

## Estrutura do Projeto

```
TP03/
├── GeradorBin.java          # Conversor CSV para Binário + construção do índice
├── Menu.java                # Menu com CRUD, ordenação, compactação e casamento
├── CRUD.java                # CRUD (usa a Árvore B)
├── Ordenacao.java           # Ordenação externa
├── ArvoreBMais.java         # Índice em Árvore B (arquivo em disco)
├── Livro.java               # Classe entidade Livro
├── ListaInvertida.java      # Índice invertido
├── Huffman.java             # Compressão de Huffman
├── LZW.java                 # Compressão LZW
├── Compressao.java          # Orquestração, versionamento e relatório
├── Reindexacao.java         # Reconstrução dos índices após restauração
├── KMP.java                 # Casamento de padrões KMP
├── BoyerMoore.java          # Casamento de padrões Boyer-Moore
├── CasamentoPadroes.java    # Orquestração da busca
├── TesteTP3.java            # Suite de testes do TP03
└── Dataset/
    └── Books.csv            # Conjunto de dados fonte

TP03/compactados/            # Criada automaticamente em tempo de execução
├── livrosHuffmanCompressaoX
└── livrosLZWCompressaoX
```

Arquivos gerados em tempo de execução:

```
TP03/
├── livros.bin               # Arquivo de dados (registros dos livros)
└── arvore.bin               # Arquivo de índice (Árvore B: id → posição)
```

---

## Formato do Arquivo Binário

O arquivo binário (`livros.bin`) segue um formato personalizado otimizado para acesso eficiente:

```
[INT: total_registros]
[
  [INT: id]                                           // ID do livro (4 bytes)
  [INT: tam_registro]                                 // Tamanho do registro a seguir
  [BOOL: lapide]         // Marcador de exclusão (1 byte): true = deletado, false = ativo
  [INT: tam_titulo] [CHAR*: titulo]                   // String de tamanho variável
  [INT: qtd_autor][INT: tam_autor] [CHAR*: autor]     // Array de Strings de tamanhos variáveis
  [INT: paginas]                                      // Número de páginas (4 bytes)
  [INT: qtd_genero] [INT: tam_genero] [CHAR*: genero] // Array de Strings de tamanhos variáveis
  [INT: tam_desc] [CHAR*: descricao]                  // String de tamanho variável
  [LONG: data_publicacao] // Data em milissegundos desde 1970 (8 bytes)
  [INT: tam_editora] [CHAR*: editora]                 // String de tamanho variável
  [CHAR*: lingua] [CHAR*: lingua]                     // String de tamanho fixo (2 char)
  [FLOAT: media_reviews]                              // Float para média das reviews
  [INT: qtd_reviews]                                  // INT para qtd das reviews
  [INT: tam_thumbnail] [CHAR*: thumbnail]             // String de tamanho variável
] × N
```

> **Observação:** o primeiro campo do arquivo (`total_registros`) funciona, na prática, como "último id usado". O `GeradorBin` grava a contagem total (ids contíguos de 1 a N), e o `CRUD.create` sobrescreve com o id mais recente. Isso é consistente porque o gerador produz ids contínuos.

---

## Índice em Árvore B

### Por que uma Árvore B?

Sem índice, qualquer operação de leitura/atualização/remoção exige **varredura sequencial** do arquivo inteiro (O(n) acessos a disco). Com uma Árvore B em disco mapeando `id → posição`, cada operação cai para **O(log_d n)** acessos — e o índice nunca precisa caber inteiro em RAM.

### Estrutura do arquivo de índice (`arvore.bin`)

```
[INT: ordem d]
[LONG: posição da raiz (-1 se vazia)]
[
  [INT: n]  // número de chaves no nó
  d × (
        [LONG: pos_filho_esq]
        [INT:  id]
        [LONG: pos_registro_no_arquivo_bin]
        [LONG: pos_filho_dir]
      )
] × M
```

- A **ordem `d`** é parametrizada em tempo de execução (o `GeradorBin` pergunta ao usuário, mínimo 3).
- Cada nó tem **no máximo `d` chaves** e **`d + 1` filhos**.
- Cada nó (exceto a raiz) tem **no mínimo `(d - 1) / 2` chaves**.
- Todos os nós são de **tamanho fixo** (`4 + d × 28` bytes), permitindo `seek` direto a qualquer nó.

### Convenção dos filhos

Cada chave carrega explicitamente dois filhos (`esq` e `dir`). Como consequência:

- `filho_esq[0]` = filho mais à esquerda do nó.
- `filho_dir[n-1]` = filho mais à direita do nó.
- Os filhos intermediários são redundantes: `filho_dir[i] == filho_esq[i+1]`, mantendo o invariante `n + 1` filhos.

### Operações implementadas

| Operação | Método | Complexidade |
|---|---|---|
| Inserir `(id, pos)` | `insere(int, long)` | O(log_d n) |
| Buscar `pos` pelo `id` | `busca(int)` | O(log_d n) |
| Atualizar posição de um `id` | `atualiza(int, long)` | O(log_d n) |
| Remover `id` (com merge/redistribuição) | `remove(int)` | O(log_d n) |

A **inserção** usa *split preventivo* (divide o filho cheio antes de descer). A **remoção** usa *fill preventivo* (garante que o filho tenha mais que o mínimo antes de descer). Quando um nó interno perde uma chave, ela é substituída pelo **predecessor** ou **sucessor** antes da remoção descer. Se a raiz fica com 0 chaves, a árvore encolhe.

### Consistência com o arquivo de dados

A posição guardada na Árvore B é **o byte onde começa o campo `id`** do registro (não a lápide, não o tamanho). Para chegar à lápide, basta `seek(pos + 8)`. Esse formato é consistente em todos os pontos:

- `GeradorBin` grava `posicao = getFilePointer()` antes de escrever o `id`.
- `CRUD.create` faz o mesmo.
- `CRUD.update` (ao mover o registro pro fim) chama `arvore.atualiza(id, novaPos)` com a mesma convenção.
- `CRUD.delete` chama `arvore.remove(id)`.

### Detalhes da Árvore B

**Split preventivo na inserção:** antes de descer para um filho, se ele estiver cheio (`n == d`), é dividido — a chave mediana sobe pro pai e as duas metades viram nós separados. Se a raiz estiver cheia, é dividida primeiro e uma nova raiz é criada; é assim que a árvore cresce em altura.

**Fill preventivo na remoção:** antes de descer, se o filho tem exatamente o mínimo de chaves, ele é reforçado:

1. Se um irmão adjacente tiver mais que o mínimo, **empresta** uma chave (via rotação pelo pai).
2. Caso contrário, faz **merge** com o irmão + a chave do meio do pai.

Assim, quando a remoção de fato acontecer na folha, ela ainda terá ≥ mínimo de chaves, e nenhum underflow precisa ser corrigido na volta.

**Substituição por predecessor/sucessor:** quando a chave a remover está em nó interno, ela é substituída pelo **maior id da subárvore esquerda** (predecessor) ou pelo **menor id da subárvore direita** (sucessor). Se ambos os filhos tiverem mais que o mínimo, usa o predecessor.

**Encolhimento:** se após uma remoção a raiz fica com 0 chaves e 1 filho, esse filho vira a nova raiz. Se a raiz fica com 0 chaves e é folha, a árvore fica vazia (`raiz = -1`).

**Atualização em massa após ordenação:** como a ordenação reorganiza todos os registros, todas as posições do índice ficam obsoletas. O `Menu` varre o arquivo sequencialmente (`[id][tam][lápide][dados]`), e para cada registro chama `arvore.atualiza(id, novaPos)`. Custo de memória O(1); custo de tempo O(n · log_d n).

---

## Funcionalidades do TP02

### 1. Conversor CSV para Binário (`GeradorBin.java`)

- Solicita a **ordem da Árvore B** (inteiro ≥ 3), validando a entrada.
- Lê os dados de `Books.csv`.
    - Origem: https://www.kaggle.com/datasets/madankhatri123h/books-dataset
    - Modificações na base original:
        - Removido "Agatha Christie: The Queen of Murder Mysteries (Biography of Her Life)" — data errada "101-01-01" (livro fictício).
        - Corrigida a data de "Tales from Shakespeare" de "19??" para 2011/07/30.
        - Normalizada a data de "L'amore bugiardo" de datetime para somente data (2012-12-13).
    - Detalhes de implementação:
        - "Unknown" em "pages" → -1.
        - "No rating" em "average_rating" → -1.
        - Datas incompletas (só ano ou ano-mês) → completadas com 01.
        - "Unknown" em datas → 1º de janeiro de 1970 (long = 0).
- Ignora o cabeçalho do CSV.
- Parsing com tratamento de campos entre aspas (vírgulas internas).
- Usa `RandomAccessFile` e captura a posição antes da escrita.
- Insere `(id, posição)` na Árvore B por livro.
- Atualiza o cabeçalho com o último id usado.

### 2. Árvore B em Disco (`ArvoreBMais.java`)

- Ordem parametrizável (mínimo 3), persistida no cabeçalho.
- Construtores: `ArvoreBMais(File, int ordem)` cria/recria; `ArvoreBMais(File)` abre existente e lê a ordem do cabeçalho.
- Rejeita abrir arquivo cuja ordem não corresponde à solicitada.
- Todas as operações fazem `seek` direto aos nós — nada em RAM além de um nó por vez.

### 3. Gerenciador de Arquivo Binário (`Menu.java`)

- Menu principal com CRUD, Ordenação, Compactação, Casamento de Padrões e Sair.
- Validação de todas as entradas numéricas.
- Abre simultaneamente o arquivo de dados (`RandomAccessFile`) e a Árvore B, passando-os ao CRUD.

### 4. CRUD (`CRUD.java`)

- **Create**: grava no fim do arquivo, atualiza o cabeçalho, insere chave na Árvore B.
- **Read**: consulta a Árvore B para localizar a posição e lê apenas o registro-alvo.
- **Update**: se o novo conteúdo cabe no espaço antigo, sobrescreve; senão marca lápide, grava no fim e chama `arvore.atualiza`.
- **Delete**: exclusão lógica com lápide e remoção da chave na Árvore B.

### 5. Ordenação (`Ordenacao.java`)

- Ordena por diferentes critérios (ID, título, páginas, etc.).
- Após reordenação, a Árvore B é atualizada em massa pelo `Menu` (varredura sequencial + `arvore.atualiza`).

---

## Fluxo de Uso do Índice

```
GeradorBin
   │
   │ 1. pergunta ordem d
   │ 2. lê CSV linha a linha
   │ 3. escreve registro em livros.bin e captura posição
   │ 4. arvore.insere(id, posição)
   ▼
livros.bin + arvore.bin prontos

Menu
   │
   ├── CRUD
   │     ├── create → grava no fim e arvore.insere
   │     ├── read   → arvore.busca(id) → seek(pos + 8) → lerRegistro
   │     ├── update → arvore.busca(id)
   │     │              ├── cabe no lugar    → sobrescreve
   │     │              └── não cabe         → lápide + grava no fim
   │     │                                     + arvore.atualiza(id, novaPos)
   │     └── delete → arvore.busca(id) → marca lápide + arvore.remove(id)
   │
   ├── Ordenar
   │     ├── fecha raf e arvore
   │     ├── Ordenacao.main(...) reordena livros.bin
   │     ├── reabre raf e arvore
   │     └── varre livros.bin e chama arvore.atualiza(id, pos) por registro
   │
   ├── Compactar (3)
   ├── Descompactar (4)
   └── Casamento de Padrões (5)
```

---

## Compactação (TP03)

Os dois algoritmos trabalham **byte a byte sobre o arquivo `livros.bin` inteiro**, então cabeçalho, ids, indicadores de tamanho, lápides e campos entram na compressão sem o algoritmo precisar conhecer o formato do registro, e a descompressão devolve o arquivo idêntico.

### Arquivos gerados

Ficam em `TP03/compactados/` (criada automaticamente) e se chamam:

- `livrosHuffmanCompressaoX`
- `livrosLZWCompressaoX`

O `X` é o mesmo para os dois em cada execução (**maior já existente + 1**), garantindo pareamento por versão. Cada arquivo guarda um **número mágico**, o **tamanho original** e um **checksum FNV-1a**, para detectar arquivo corrompido.

### Huffman

- Árvore montada com fila de prioridade.
- **Desempate fixo**: valor do byte nas folhas, ordem de criação nos nós internos.
- A tabela de frequências é gravada no cabeçalho, permitindo reconstruir exatamente a mesma árvore na descompressão.

### LZW

- **Dicionário inicial**: códigos 0–255 são os 256 valores possíveis de um byte (o arquivo é binário, então todos precisam existir); sequências novas começam em 256.
- **Códigos com tamanho fixo de 12 bits** → dicionário comporta 4096 entradas.
- Quando o dicionário enche, compressor e descompressor **param de cadastrar** sequências novas e continuam usando as existentes.

### Descompressão

- Os dois algoritmos são executados (os que existirem).
- Os tempos são comparados.
- Os resultados são conferidos entre si.
- O `livros.bin` é substituído pelo recuperado (escrito antes num `.tmp` e movido por cima).
- Como o arquivo restaurado pode ser uma **versão mais antiga** da base, a Árvore B e as listas invertidas são reconstruídas em seguida (`Reindexacao`).

---

## Casamento de Padrões (TP03)

A busca varre o arquivo registro a registro, **ignora registros com lápide** e procura o padrão em todos os campos de texto ou em um campo escolhido (título, autores, gêneros, descrição, editora, língua, thumbnail), com opção de **ignorar maiúsculas**.

### Por que KMP e Boyer-Moore

- **KMP**: garante tempo linear **O(n + m)** mesmo no pior caso e nunca volta no texto. Combina com campos curtos e repetitivos (gêneros, editora, URLs de thumbnail) onde há muitos casamentos parciais.
- **Boyer-Moore**: compara da direita para a esquerda e usa as regras do **caractere ruim** e do **bom sufixo** para pular trechos. Em campos longos de texto natural, como a descrição, tende a ser o mais rápido.

Como têm comportamentos diferentes, rodar os dois sobre os mesmos textos permite **comparar tempo** e **validar um contra o outro** (a opção "os dois" do menu faz isso).

---

## Testes (`TesteTP3` — 46 verificações, todas passando)

- **Round-trip dos dois algoritmos** com:
    - arquivo vazio, 1 byte, um único símbolo repetido;
    - os 256 valores de byte;
    - dados aleatórios e de baixa entropia grandes (enchem o dicionário do LZW);
    - o caso especial KwKwK;
    - árvore de Huffman muito profunda;
    - o `livros.bin` real.
- **Detecção de arquivo corrompido** (checksum).
- **KMP e Boyer-Moore** contra uma busca de referência:
    - casos fixos e 20 mil casos aleatórios;
    - base real (7 padrões × 8 escopos).
- **Versionamento**: versões 1 e 2, nomes dos arquivos, restauração byte a byte após alterar a base com o CRUD.
- **Reconstrução de índices** após restauração.
- **Casos de borda**: versão inexistente, versão corrompida, base só com cabeçalho, arquivo de 0 bytes.
- **Regressão do TP02**: update, read e ordenação externa.

### Resultado no `livros.bin` (1.171.401 bytes)

| Algoritmo | Tamanho gerado | Ganho |
|---|---|---|
| Huffman | 775.504 bytes | **33,80%** |
| LZW | 622.119 bytes | **46,89%** |

---

## Tecnologias Utilizadas

- **Java 8+**
- **java.io** — `RandomAccessFile`, `BufferedReader`, `FileReader`, `File`
- **java.util** — `ArrayList`, `List`, `Scanner`, `PriorityQueue`, `HashMap`
- **java.nio.file** — `Path`, `Paths`, `Files`

---

## Como Usar

### Pré-requisitos

- Java 8 ou superior
- VS Code ou IDE Java
- `Books.csv` em `TP03/Dataset/`

### Passo 1: Compilar

```bash
javac TP03/*.java
```

### Passo 2: Gerar os arquivos binário e de índice

```bash
java TP03.GeradorBin
```

Pergunta a **ordem da Árvore B** (inteiro ≥ 3) e gera:

- `TP03/livros.bin`
- `TP03/arvore.bin`

### Passo 3: Manipular o banco

```bash
java TP03.Menu
```

O menu oferece:

```
===== SISTEMA GERENCIADOR DE BANCO DE DADOS - LIVROS =====
1 - CRUD
2 - Ordenar
3 - Compactar
4 - Descompactar
5 - Casamento de Padrões
0 - Sair
```

No CRUD:

```
----- CRUD -----
1 - Inserir um novo livro
2 - Buscar livro (pelo ID)
3 - Atualizar livro
4 - Deletar livro
0 - Voltar
```

### Passo 4: Rodar os testes do TP03 (opcional)

```bash
java TP03.TesteTP3
```

---

## Estrutura da Classe `Livro`

```java
public class Livro {
    protected int id;
    protected String titulo;
    protected String[] autores;
    protected int paginas;
    protected String[] generos;
    protected String descricao;
    protected long dataPublicacao; // Em milissegundos desde 1970
    protected String editora;
    protected String lingua;
    protected float mediaReviews;
    protected int qtdReviews;
    protected String thumbnail;
}
```

---

## Autores

- **Pedro Augusto Gonçalves Gomes Amaral**
- **Daniella Emily Cornelio da Silva**