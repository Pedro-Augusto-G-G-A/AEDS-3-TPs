package TP03;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * Boyer-Moore: compara o padrão com o texto de trás pra frente e, quando
 * encontra uma diferença, desloca o padrão pelo maior valor entre as regras
 * do caractere ruim e do bom sufixo. Assim, muitas posições do texto nem
 * chegam a ser lidas.
 *
 * O pré-processamento do padrão é feito uma vez, no construtor.
 */ 

public class BoyerMoore {

    private final char[] padrao;
    private final int[] ultimaOcorrencia;  // caractere -> última posição no padrão (-1 se não aparece)
    private final int[] deslocamentoSufixo; // tabela do bom sufixo (tamanho m+1)

    public BoyerMoore(String padrao) {
        this.padrao = padrao.toCharArray();
        int m = this.padrao.length;

        // regra do caractere ruim: char tem 16 bits, então a tabela tem 65536 posições
        ultimaOcorrencia = new int[65536];
        Arrays.fill(ultimaOcorrencia, -1);
        for (int i = 0; i < m; i++) {
            ultimaOcorrencia[this.padrao[i]] = i;
        }

        // regra do bom sufixo em dois passos
        deslocamentoSufixo = new int[m + 1];
        int[] borda = new int[m + 1]; // borda[i] = início da maior borda do sufixo padrao[i..m-1]

        // passo 1: o sufixo que casou reaparece em outro ponto do padrão (precedido por char diferente)
        int i = m;
        int j = m + 1;
        borda[i] = j;
        while (i > 0) {
            while (j <= m && this.padrao[i - 1] != this.padrao[j - 1]) {
                if (deslocamentoSufixo[j] == 0) {
                    deslocamentoSufixo[j] = j - i;
                }
                j = borda[j];
            }
            i--;
            j--;
            borda[i] = j;
        }

        // passo 2: só um prefixo do padrão coincide com parte do sufixo que casou
        j = borda[0];
        for (i = 0; i <= m; i++) {
            if (deslocamentoSufixo[i] == 0) {
                deslocamentoSufixo[i] = j;
            }
            if (i == j) {
                j = borda[j];
            }
        }
    }

    // retorna a posição inicial (0-based) de TODAS as ocorrências, inclusive sobrepostas.
    // Padrão vazio não casa com nada (retorna lista vazia).
    public List<Integer> buscar(String texto) {
        List<Integer> posicoes = new ArrayList<>();
        int m = padrao.length;
        int n = texto.length();
        if (m == 0 || n < m) return posicoes;

        int s = 0; // deslocamento atual do padrão em relação ao texto
        while (s <= n - m) {
            int k = m - 1;
            // compara da direita para a esquerda
            while (k >= 0 && padrao[k] == texto.charAt(s + k)) {
                k--;
            }
            if (k < 0) {
                posicoes.add(s);                 // casou o padrão inteiro
                s += deslocamentoSufixo[0];      // desloca para a próxima ocorrência possível
            } else {
                int ruim = k - ultimaOcorrencia[texto.charAt(s + k)];
                s += Math.max(deslocamentoSufixo[k + 1], ruim); // o maior dos dois deslocamentos (sempre >= 1)
            }
        }
        return posicoes;
    }
}
