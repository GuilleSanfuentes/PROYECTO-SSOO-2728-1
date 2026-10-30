package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.Nodo;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.sync.Buffer;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formulario para crear un proceso; muestra solo los campos del tipo elegido. */
public class DialogoProceso extends DialogoFormulario {

    private static final int MAX_VALOR = 1_000_000;
    private static final String AUTOMATICO = "Automático";
    private static final int FILAS_COMUNES = 6;
    private static final int FILAS_TIPO = 6;
    private static final int PRIORIDAD_INICIAL = 5;
    private static final int DIGITOS_VALOR = 7;
    private static final int DIGITOS_DEADLINE = 9;

    private final JTextField nombre = new CampoTexto(12);
    private final JComboBox<TipoProceso> tipo = new JComboBox<>(TipoProceso.values());
    private final JComboBox<Integer> prioridad = new JComboBox<>();
    private final JTextField memoria = new CampoTexto(12);
    private final JTextField deadline = new CampoTexto(12);
    private final JComboBox<String> computador = new JComboBox<>();
    private final JTextField instrucciones = new CampoTexto(12);
    private final JTextField cadaCiclosES = new CampoTexto(12);
    private final JTextField duracionES = new CampoTexto(12);
    private final JComboBox<String> buffer = new JComboBox<>();
    private final JTextField ciclosPorElemento = new CampoTexto(12);
    private final JTextField elementos = new CampoTexto(12);
    private final JComponent[][] filasTipo = new JComponent[FILAS_TIPO][];

    /** Arma el formulario sobre el clúster indicado. */
    public DialogoProceso(Window dueno, Cluster cluster) {
        super(dueno, cluster, "Crear proceso");
        computador.addItem(AUTOMATICO);
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            computador.addItem(cluster.getComputador(i).getNombre());
        }
        for (int p = FabricaProcesos.PRIORIDAD_MAXIMA; p <= FabricaProcesos.PRIORIDAD_MINIMA; p++) {
            prioridad.addItem(p);
        }
        prioridad.setSelectedItem(PRIORIDAD_INICIAL);
        prioridad.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice, boolean seleccionado,
                    boolean enfoque) {
                return super.getListCellRendererComponent(lista, textoPrioridad(valor), indice, seleccionado, enfoque);
            }
        });
        FiltroEntrada.nombre(nombre);
        FiltroEntrada.soloDigitos(memoria, DIGITOS_VALOR);
        FiltroEntrada.soloDigitos(deadline, DIGITOS_DEADLINE);
        FiltroEntrada.soloDigitos(instrucciones, DIGITOS_VALOR);
        FiltroEntrada.soloDigitos(cadaCiclosES, DIGITOS_VALOR);
        FiltroEntrada.soloDigitos(duracionES, DIGITOS_VALOR);
        FiltroEntrada.soloDigitos(ciclosPorElemento, DIGITOS_VALOR);
        FiltroEntrada.soloDigitos(elementos, DIGITOS_VALOR);
        tipo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice, boolean seleccionado,
                    boolean enfoque) {
                return super.getListCellRendererComponent(lista, valor instanceof TipoProceso t ? t.getNombre() : valor,
                        indice, seleccionado, enfoque);
            }
        });
        JPanel campos = grilla();
        fila(campos, 0, "Nombre:", nombre);
        fila(campos, 1, "Tipo:", tipo);
        fila(campos, 2, "Prioridad:", prioridad);
        fila(campos, 3, "Memoria:", memoria);
        fila(campos, 4, "Deadline (ciclos):", deadline);
        fila(campos, 5, "Computador:", computador);
        filaTipo(campos, 0, "Instrucciones:", instrucciones);
        filaTipo(campos, 1, "E/S cada (instr.):", cadaCiclosES);
        filaTipo(campos, 2, "Duración E/S (ciclos):", duracionES);
        filaTipo(campos, 3, "Buffer:", buffer);
        filaTipo(campos, 4, "Ciclos por elemento:", ciclosPorElemento);
        filaTipo(campos, 5, "Elementos:", elementos);
        tipo.addActionListener(e -> mostrarTipo());
        mostrarTipo();
        armar(campos);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                cargarBuffers();
            }
        });
    }

    /** Texto del desplegable de prioridad: marca los extremos de la escala. */
    private static Object textoPrioridad(Object valor) {
        if (!(valor instanceof Integer p)) {
            return valor;
        }
        if (p == FabricaProcesos.PRIORIDAD_MAXIMA) {
            return p + "  (más importante)";
        }
        return p == FabricaProcesos.PRIORIDAD_MINIMA ? p + "  (menos importante)" : String.valueOf(p);
    }

    /** Agrega una fila propia de un tipo, en la misma grilla y columnas que las comunes. */
    private void filaTipo(JPanel campos, int indice, String etiqueta, JComponent campo) {
        filasTipo[indice] = new JComponent[] {fila(campos, FILAS_COMUNES + indice, etiqueta, campo), campo};
    }

    /** Muestra solo las filas del tipo elegido. */
    private void mostrarTipo() {
        TipoProceso elegido = (TipoProceso) tipo.getSelectedItem();
        int primera = elegido.usaBuffer() ? 3 : 0;
        int ultima = elegido == TipoProceso.CPU_BOUND ? 0 : elegido == TipoProceso.IO_BOUND ? 2 : 5;
        for (int i = 0; i < FILAS_TIPO; i++) {
            boolean visible = i >= primera && i <= ultima;
            filasTipo[i][0].setVisible(visible);
            filasTipo[i][1].setVisible(visible);
        }
        if (elegido.usaBuffer()) {
            cargarBuffers();
        }
        reajustar();
    }

    @Override
    protected void limpiarCampos() {
        nombre.setText("");
        prioridad.setSelectedItem(PRIORIDAD_INICIAL);
        memoria.setText("");
        deadline.setText("");
        instrucciones.setText("");
        cadaCiclosES.setText("");
        duracionES.setText("");
        ciclosPorElemento.setText("");
        elementos.setText("");
        computador.setSelectedIndex(0);
        tipo.setSelectedIndex(0);
    }

    /** Rellena la lista con los buffers que existen ahora, conservando la selección. */
    private void cargarBuffers() {
        Object anterior = buffer.getSelectedItem();
        buffer.removeAllItems();
        Nodo<Buffer> nodo = cluster.getBuffers().copiar().getCabeza();
        while (nodo != null) {
            buffer.addItem(nodo.getDato().getNombre());
            nodo = nodo.getSiguiente();
        }
        if (anterior != null) {
            buffer.setSelectedItem(anterior);
        }
    }

    @Override
    protected void crear() {
        if (!simulacionAdmite()) {
            return;
        }
        TipoProceso elegido = (TipoProceso) tipo.getSelectedItem();
        int prio = (Integer) prioridad.getSelectedItem();
        Validador.ResultadoEntero mem = Validador.entero(memoria.getText(), "Memoria", 1, ConfiguracionSistema.MAX_RAM);
        Validador.ResultadoEntero dead = Validador.entero(deadline.getText(), "Deadline", 1, Integer.MAX_VALUE);
        Validador.ResultadoEntero instr = null;
        Validador.ResultadoEntero cada = null;
        Validador.ResultadoEntero dur = null;
        Validador.ResultadoEntero ciclos = null;
        Validador.ResultadoEntero elem = null;
        Validador.ResultadoEntero[] revisar;
        if (elegido == TipoProceso.CPU_BOUND) {
            instr = Validador.entero(instrucciones.getText(), "Instrucciones", 1, MAX_VALOR);
            revisar = new Validador.ResultadoEntero[] {mem, dead, instr};
        } else if (elegido == TipoProceso.IO_BOUND) {
            instr = Validador.entero(instrucciones.getText(), "Instrucciones", 1, MAX_VALOR);
            cada = Validador.entero(cadaCiclosES.getText(), "E/S cada", 1, MAX_VALOR);
            dur = Validador.entero(duracionES.getText(), "Duración E/S", 1, MAX_VALOR);
            revisar = new Validador.ResultadoEntero[] {mem, dead, instr, cada, dur};
        } else {
            ciclos = Validador.entero(ciclosPorElemento.getText(), "Ciclos por elemento", 1, MAX_VALOR);
            elem = Validador.entero(elementos.getText(), "Elementos", 1, MAX_VALOR);
            revisar = new Validador.ResultadoEntero[] {mem, dead, ciclos, elem};
        }
        if (nombre.getText().isBlank()) {
            error("Nombre: escribe un nombre para el proceso");
            return;
        }
        for (Validador.ResultadoEntero r : revisar) {
            if (!r.valido()) {
                error(r.motivo());
                return;
            }
        }
        if (elegido.usaBuffer() && buffer.getSelectedItem() == null) {
            error("Buffer: no hay buffers creados, crea uno primero");
            return;
        }
        String nombreProceso = nombre.getText().trim();
        int destino = computador.getSelectedIndex() - 1;
        DatosProceso datos = switch (elegido) {
            case CPU_BOUND -> DatosProceso.cpuBound(nombreProceso, prio, mem.valor(), dead.valor(), instr.valor(), destino);
            case IO_BOUND -> DatosProceso.ioBound(nombreProceso, prio, mem.valor(), dead.valor(), instr.valor(),
                    cada.valor(), dur.valor(), destino);
            case PRODUCTOR -> DatosProceso.productor(nombreProceso, prio, mem.valor(), dead.valor(),
                    (String) buffer.getSelectedItem(), ciclos.valor(), elem.valor(), destino);
            case CONSUMIDOR -> DatosProceso.consumidor(nombreProceso, prio, mem.valor(), dead.valor(),
                    (String) buffer.getSelectedItem(), ciclos.valor(), elem.valor(), destino);
        };
        String motivo = cluster.validarProceso(datos);
        if (motivo != null) {
            error("Rechazado: " + motivo);
            return;
        }
        PCB pcb = cluster.crearProceso(datos);
        if (pcb == null) {
            error("No se pudo crear el proceso '" + nombreProceso + "'");
            return;
        }
        exito("Proceso " + pcb.getEtiqueta() + " ('" + nombreProceso + "') creado");
        nombre.setText("");
    }
}
