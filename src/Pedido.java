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

    public byte[] ordenar() {
        byte[] copia = Arrays.copyOf(numeros, numeros.length);
        Arrays.sort(copia); 
        return copia;
    }
}
