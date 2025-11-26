import java.io.*;
import java.net.*;
import java.util.*;

public class Distribuidor {

    // Configuração dos servidores
    private static final String[] RECEPTORS = {
        "172.16.130.68:12345",  
        "172.16.130.87:12346",  
        "172.16.130.90:12347"   
    };

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== SISTEMA DISTRIBUÍDO DE ORDENAÇÃO (ATIVIDADE 2) ===");
        
        // Conectando
        System.out.println("\n[D] Iniciando testes de conexão com os servidores...");
        boolean conexaoOk = testarConexoes();

        if (!conexaoOk) {
            System.err.println("\n[ERRO] Não foi possível conectar a todos os servidores.");
            System.err.println("Verifique os IPs e se o ReceptorServer está rodando neles.");
            System.out.println("Pressione ENTER para sair.");
            scanner.nextLine();
            System.exit(1);
        }

        System.out.println("[D] Conexões estabelecidas! Sistema pronto.");
        
        // LOOP PRINCIPAL
        while (true) {
            System.out.println("\n" + "=".repeat(50));
            
            // Pergunta o tamanho do vetor
            int vectorSize = 100_000; 
            System.out.print("[D] Digite o tamanho do vetor (Enter para padrão 100.000): ");
            String inputTamanho = scanner.nextLine();
            if (!inputTamanho.isBlank()) {
                try {
                    vectorSize = Integer.parseInt(inputTamanho);
                } catch (Exception e) {}
            }

            // Gerando o vetor
            System.out.printf("[D] Gerando vetor aleatório de %,d elementos... ", vectorSize);
            byte[] vetorOriginal = new byte[vectorSize];
            new Random().nextBytes(vetorOriginal);
            System.out.println("Concluído.");

            // Pergunta para imprimir ventor
            System.out.print("[D] Deseja imprimir o vetor gerado na tela? (S/N): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("S")) {
                imprimirVetor(vetorOriginal);
            }

            // Aviso de que vai ordernar
            System.out.println("\n[D] Tudo pronto para a ordenação distribuída.");
            System.out.println("[D] Pressione ENTER para iniciar o envio para os Receptores...");
            scanner.nextLine();

            // Envio por threads e recebimento
            long inicio = System.currentTimeMillis();
            try {
                // Lógica de envio e recebimento
                List<byte[]> partesRecebidas = processarDistribuido(vetorOriginal);
                
                // Merge Final
                System.out.println("[D] Todas as partes recebidas. Realizando merge final...");
                byte[] vetorFinal = realizarMergeFinal(partesRecebidas);
                
                long fim = System.currentTimeMillis();
                System.out.printf("[D] SUCESSO! Ordenação concluída em %.3f segundos.%n", (fim - inicio) / 1000.0);

                //Salvar o arquivo
                System.out.print("[D] Digite o nome do arquivo para salvar o RESULTADO (ex: saida): ");
                String nomeArquivo = scanner.nextLine().trim();
                
                
                if (nomeArquivo.isBlank()) {
                    nomeArquivo = "saida_ordenada.txt";
                } else if (!nomeArquivo.toLowerCase().endsWith(".txt")) {
                    nomeArquivo += ".txt"; // Adiciona .txt se o usuário esqueceu
                }
                
                salvarEmArquivo(vetorFinal, nomeArquivo);

                //Salvar o arquivo, vetor original desordenado
                System.out.println("-".repeat(50));
                System.out.print("[D] Deseja salvar também o vetor ORIGINAL (desordenado)? (S/N): ");
                if (scanner.nextLine().trim().equalsIgnoreCase("S")) {
                    // Gera o nome automático baseado no anterior
                    String nomeOriginal;
                    int pontoIndex = nomeArquivo.lastIndexOf('.'); 
                    if (pontoIndex != -1) {
                        nomeOriginal = nomeArquivo.substring(0, pontoIndex) + "(nao_ordenado)" + nomeArquivo.substring(pontoIndex);
                    } else {
                        nomeOriginal = nomeArquivo + "(nao_ordenado).txt";
                    }
                    
                    System.out.println("[D] Salvando original como: " + nomeOriginal);
                    salvarEmArquivo(vetorOriginal, nomeOriginal);
                }

            } catch (Exception e) {
                System.out.println("[ERRO] Falha durante o processamento: " + e.getMessage());
                e.printStackTrace();
            }

            // Pergunta se quer ordenar outro vetor aleatorio
            System.out.println("-".repeat(50));
            System.out.print("[D] Deseja realizar uma NOVA ORDENAÇÃO com um NOVO VETOR? (S/N): ");
            String respNovo = scanner.nextLine();
            
            if (!respNovo.trim().equalsIgnoreCase("S")) {
                System.out.println("[D] Encerrando o Distribuidor. Até logo!");
                break; 
            }
            System.out.println("[D] Reiniciando...\n");
        }
        scanner.close();
    }

    //Metodos auxiliares

    private static boolean testarConexoes() {
        boolean todasOk = true;
        for (String server : RECEPTORS) {
            String[] parts = server.split(":");
            System.out.printf(" -> Conectando a %s ... ", server);
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 2000);
                System.out.println("[OK]");
            } catch (IOException e) {
                System.out.println("[FALHA]");
                todasOk = false;
            }
        }
        return todasOk;
    }

    private static List<byte[]> processarDistribuido(byte[] vetorTotal) {
        int numServidores = RECEPTORS.length;
        List<byte[]> resultados = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        int tamanhoBloco = vetorTotal.length / numServidores;

        for (int i = 0; i < numServidores; i++) {
            final int index = i;
            int inicio = i * tamanhoBloco;
            int fim = (i == numServidores - 1) ? vetorTotal.length : (i + 1) * tamanhoBloco;
            byte[] fatia = Arrays.copyOfRange(vetorTotal, inicio, fim);
            
            Thread t = new Thread(() -> {
                String ip = RECEPTORS[index];
                try {
                    String[] parts = ip.split(":");
                    try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        System.out.printf("[Thread-%d] Enviando %d elementos para %s...%n", index, fatia.length, ip);
                        out.writeObject(new Pedido(fatia));
                        out.flush();

                        Object response = in.readObject();
                        if (response instanceof Resposta) {
                            resultados.add(((Resposta) response).getVetorOrdenado());
                            System.out.printf("[Thread-%d] Recebido vetor ordenado de %s!%n", index, ip);
                        }
                        out.writeObject(new ComunicadoEncerramento());
                    }
                } catch (Exception e) {
                    System.err.printf("[Thread-%d] Erro com %s: %s%n", index, ip, e.getMessage());
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) try { t.join(); } catch (Exception e) {}
        return resultados;
    }

    private static byte[] realizarMergeFinal(List<byte[]> partes) {
        List<byte[]> listaMutavel = new ArrayList<>(partes);
        while (listaMutavel.size() > 1) {
            byte[] p1 = listaMutavel.remove(0);
            byte[] p2 = listaMutavel.remove(0);
            listaMutavel.add(MergeUtils.intercalar(p1, p2));
        }
        return listaMutavel.isEmpty() ? new byte[0] : listaMutavel.get(0);
    }

    private static void imprimirVetor(byte[] v) {
        if (v.length > 50) {
            System.out.println("[D] Vetor (primeiros 50): " + Arrays.toString(Arrays.copyOf(v, 50)) + "...");
        } else {
            System.out.println("[D] Vetor: " + Arrays.toString(v));
        }
    }

    private static void salvarEmArquivo(byte[] vetor, String nome) {
        try (PrintWriter w = new PrintWriter(new FileWriter(nome))) {
            w.println(Arrays.toString(vetor));
            System.out.println("[D] Arquivo salvo com sucesso: " + new File(nome).getAbsolutePath());
        } catch (IOException e) { System.out.println("[D] Erro ao salvar arquivo."); }
    }
}