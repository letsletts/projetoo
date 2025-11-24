public class Resposta extends Comunicado {
    private static final long serialVersionUID = 1L;
    private final byte[] vetorOrdenado;

    public Resposta(byte[] vetorOrdenado) {
        this.vetorOrdenado = vetorOrdenado;
    }

    public byte[] getVetorOrdenado() {
        return vetorOrdenado;
    }
}