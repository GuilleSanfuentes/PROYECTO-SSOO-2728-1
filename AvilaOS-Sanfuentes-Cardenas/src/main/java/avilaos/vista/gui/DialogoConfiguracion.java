package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.config.NuevaConfiguracion;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Reloj;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.Window;
import java.io.File;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Formulario de nueva configuración: ciclo, latencia y, por computador, RAM, política y quantum; guarda el JSON y ofrece cargarlo. */
public class DialogoConfiguracion extends DialogoFormulario {

    private final JTextField duracion = new CampoTexto(12);
    private final JTextField latencia = new CampoTexto(12);
    private final JTextField cantidad = new CampoTexto(12);
    private final JCheckBox incluirCarga = new JCheckBox("Incluir procesos y buffers actuales");
    private final JTextField[] ram = new JTextField[ConfiguracionSistema.MAX_COMPUTADORES];
    private final JComboBox<PoliticaTipo>[] politica = crearCombos();
    private final JTextField[] quantum = new JTextField[ConfiguracionSistema.MAX_COMPUTADORES];
    private final JLabel[] etiquetas = new JLabel[ConfiguracionSistema.MAX_COMPUTADORES];
    private final JPanel[] filas = new JPanel[ConfiguracionSistema.MAX_COMPUTADORES];

    /** Arma el formulario sobre el clúster indicado, con los valores vigentes como punto de partida. */
    public DialogoConfiguracion(Window dueno, Cluster cluster) {
        super(dueno, cluster, "Nueva configuración");
        JPanel campos = grilla();
        fila(campos, 0, "Ciclo (ms):", duracion);
        fila(campos, 1, "Latencia de red (ciclos):", latencia);
        fila(campos, 2, "Número de computadores:", cantidad);
        FiltroEntrada.soloDigitos(duracion, String.valueOf(Reloj.DURACION_MAXIMA_MS).length());
        FiltroEntrada.soloDigitos(latencia, String.valueOf(ConfiguracionSistema.MAX_LATENCIA).length());
        FiltroEntrada.soloDigitos(cantidad, 1);
        cantidad.setToolTipText("De " + ConfiguracionSistema.MIN_COMPUTADORES + " a " + ConfiguracionSistema.MAX_COMPUTADORES);
        for (int i = 0; i < filas.length; i++) {
            ram[i] = new CampoTexto(6);
            quantum[i] = new CampoTexto(3);
            FiltroEntrada.soloDigitos(ram[i], String.valueOf(ConfiguracionSistema.MAX_RAM).length());
            FiltroEntrada.soloDigitos(quantum[i], String.valueOf(ConfiguracionSistema.MAX_QUANTUM).length());
            politica[i].setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                        boolean seleccionado, boolean foco) {
                    return super.getListCellRendererComponent(lista,
                            valor instanceof PoliticaTipo tipo ? tipo.getNombre() : valor, indice, seleccionado, foco);
                }
            });
            filas[i] = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            filas[i].add(new JLabel("RAM"));
            filas[i].add(ram[i]);
            filas[i].add(politica[i]);
            filas[i].add(new JLabel("Quantum"));
            filas[i].add(quantum[i]);
            etiquetas[i] = fila(campos, 3 + i, "Computador " + i + ":", filas[i]);
        }
        fila(campos, 3 + filas.length, "", incluirCarga);
        cantidad.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                ajustarFilas();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                ajustarFilas();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                ajustarFilas();
            }
        });
        cargarVigentes();
        armar(campos, "Guardar");
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<PoliticaTipo>[] crearCombos() {
        JComboBox<PoliticaTipo>[] combos = new JComboBox[ConfiguracionSistema.MAX_COMPUTADORES];
        for (int i = 0; i < combos.length; i++) {
            combos[i] = new JComboBox<>(PoliticaTipo.values());
        }
        return combos;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            cargarVigentes();
        }
        super.setVisible(visible);
    }

    @Override
    protected void limpiarCampos() {
        cargarVigentes();
    }

    /** Rellena los campos con los valores del clúster actual. */
    private void cargarVigentes() {
        ConfiguracionSistema actual = cluster.configuracionActual();
        duracion.setText(String.valueOf(actual.getDuracionCicloMs()));
        latencia.setText(String.valueOf(actual.getLatenciaRed()));
        incluirCarga.setSelected(false);
        int i = 0;
        Nodo<ConfigComputador> nodo = actual.getComputadores().getCabeza();
        while (nodo != null && i < filas.length) {
            ram[i].setText(String.valueOf(nodo.getDato().ram()));
            politica[i].setSelectedItem(nodo.getDato().politica());
            quantum[i].setText(String.valueOf(nodo.getDato().quantum()));
            i++;
            nodo = nodo.getSiguiente();
        }
        for (; i < filas.length; i++) {
            ram[i].setText(String.valueOf(ConfiguracionSistema.DEF_RAM));
            politica[i].setSelectedItem(ConfiguracionSistema.DEF_POLITICA);
            quantum[i].setText(String.valueOf(ConfiguracionSistema.DEF_QUANTUM));
        }
        cantidad.setText(String.valueOf(actual.getComputadores().tamano()));
        ajustarFilas();
    }

    /** Muestra tantas filas de computador como indique el número escrito, si es válido. */
    private void ajustarFilas() {
        Validador.ResultadoEntero n = Validador.entero(cantidad.getText(), "Número de computadores",
                ConfiguracionSistema.MIN_COMPUTADORES, ConfiguracionSistema.MAX_COMPUTADORES);
        if (!n.valido()) {
            return;
        }
        for (int i = 0; i < filas.length; i++) {
            etiquetas[i].setVisible(i < n.valor());
            filas[i].setVisible(i < n.valor());
        }
        reajustar();
    }

    @Override
    protected void crear() {
        Validador.ResultadoEntero ciclo = Validador.entero(duracion.getText(), "Ciclo (ms)", 0, Reloj.DURACION_MAXIMA_MS);
        Validador.ResultadoEntero lat = Validador.entero(latencia.getText(), "Latencia de red", 0, ConfiguracionSistema.MAX_LATENCIA);
        Validador.ResultadoEntero n = Validador.entero(cantidad.getText(), "Número de computadores",
                ConfiguracionSistema.MIN_COMPUTADORES, ConfiguracionSistema.MAX_COMPUTADORES);
        Validador.ResultadoEntero[] primeros = {ciclo, lat, n};
        for (Validador.ResultadoEntero r : primeros) {
            if (!r.valido()) {
                error(r.motivo());
                return;
            }
        }
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        for (int i = 0; i < n.valor(); i++) {
            Validador.ResultadoEntero memoria = Validador.entero(ram[i].getText(), "Computador " + i + ": RAM", 1, ConfiguracionSistema.MAX_RAM);
            Validador.ResultadoEntero q = Validador.entero(quantum[i].getText(), "Computador " + i + ": quantum", 1, ConfiguracionSistema.MAX_QUANTUM);
            if (!memoria.valido()) {
                error(memoria.motivo());
                return;
            }
            if (!q.valido()) {
                error(q.motivo());
                return;
            }
            computadores.agregarFinal(new ConfigComputador(memoria.valor(), (PoliticaTipo) politica[i].getSelectedItem(), q.valor()));
        }
        NuevaConfiguracion.Resultado resultado = NuevaConfiguracion.construir(cluster.configuracionActual(),
                ciclo.valor(), lat.valor(), computadores, incluirCarga.isSelected());
        if (!resultado.valido()) {
            error(resultado.motivo());
            return;
        }
        File archivo = SelectorConfiguracion.elegirParaGuardar(this);
        if (archivo == null) {
            return;
        }
        if (!ArchivoConfiguracion.guardarConfiguracion(resultado.config(), archivo.getPath())) {
            error("No se pudo guardar en " + archivo.getPath());
            return;
        }
        limpiarError();
        if (getOwner() instanceof VentanaPrincipal ventana && Avisos.confirmar(this, "Nueva configuración",
                "Configuración guardada en \"" + archivo.getName() + "\".\n¿Quieres cargarla ahora? Se detiene la simulación actual.")) {
            setVisible(false);
            ventana.cargarConfiguracion(archivo);
            return;
        }
        exito("Configuración guardada: " + archivo.getName());
    }
}
