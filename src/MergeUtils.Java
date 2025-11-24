public class MergeUtils {

    // Algoritmo de Intercalação (Merge) descrito no PDF
    public static byte[] intercalar(byte[] a, byte[] b) {
        int tamanhoFinal = a.length + b.length;
        byte[] c = new byte[tamanhoFinal];
        
        int i = 0; // cursor para vetor A
        int j = 0; // cursor para vetor B
        int k = 0; // cursor para vetor C (final)

        while (i < a.length && j < b.length) {
            if (a[i] <= b[j]) {
                c[k] = a[i];
                i++;
            } else {
                c[k] = b[j];
                j++;
            }
            k++;
        }

        // Copia o restante de A, se houver
        while (i < a.length) {
            c[k] = a[i];
            i++;
            k++;
        }

        // Copia o restante de B, se houver
        while (j < b.length) {
            c[k] = b[j];
            j++;
            k++;
        }

        return c;
    }
}