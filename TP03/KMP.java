package TP03;

import java.util.ArrayList;
import java.util.List;


    /*
    KMP (Knuth-Morris-Pratt): antes de buscar, calcula para cada posição do padrão
    o tamanho do maior prefixo que também é sufixo até ali (tabela de falhas).
    Quando há uma divergência, o ponteiro do texto não volta: só o do padrão recua,
    guiado pela tabela. Assim o texto é percorrido uma única vez, em O(n + m).

    A tabela é calculada no construtor, uma vez por padrão, e reaproveitada em
    todos os textos varridos.
    */

public class KMP {

    private final char[] padrao;
    private final int[] falha; // falha[i] = tamanho do maior prefixo = sufixo de padrao[0..i]

    public KMP(String padrao) {
        this.padrao = padrao.toCharArray();
        this.falha = new int[this.padrao.length];

        int k = 0; // tamanho do prefixo atual que também é sufixo
        for (int i = 1; i < this.padrao.length; i++) {
            while (k > 0 && this.padrao[i] != this.padrao[k]) {
                k = falha[k - 1]; // recua para o próximo prefixo-sufixo possível
            }
            if (this.padrao[i] == this.padrao[k]) {
                k++;
            }
            falha[i] = k;
        }
    }

    // retorna a posição inicial (0-based) de TODAS as ocorrências, inclusive sobrepostas.
    // Padrão vazio não casa com nada (retorna lista vazia).
    public List<Integer> buscar(String texto) {
        List<Integer> posicoes = new ArrayList<>();
        int m = padrao.length;
        if (m == 0 || texto.length() < m) return posicoes;

        int k = 0; // quantos caracteres do padrão já casaram
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            while (k > 0 && c != padrao[k]) {
                k = falha[k - 1]; // divergiu: aproveita o que já casou, sem voltar no texto
            }
            if (c == padrao[k]) {
                k++;
            }
            if (k == m) { // padrão inteiro casou, terminando em i
                posicoes.add(i - m + 1);
                k = falha[k - 1]; // continua procurando (permite ocorrências sobrepostas)
            }
        }
        return posicoes;
    }
}
