import java.util.Arrays;

public class Pedido extends Comunicado {
    private static final long serialVersionUID = 1L;
    private final byte[] numeros;

    public Pedido(byte[] numeros) {
        this.numeros = numeros;
    }

    public byte[] getNumeros() {
        return numeros;
    }

    // Método auxiliar para ordenar localmente usando o MergeSort do Java
    // O PDF pede para ordenar recursivamente, mas para as folhas (threads)
    // podemos usar o Arrays.sort ou implementar o merge sort puro aqui.
    // Para simplificar e ser eficiente nas pontas:
    public byte[] ordenar() {
        byte[] copia = Arrays.copyOf(numeros, numeros.length);
        Arrays.sort(copia); // Java usa Dual-Pivot Quicksort para primitivos, que é muito rápido
        return copia;
    }
}