package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.Nodo;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.sync.Buffer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formulario para crear un proceso; muestra solo los campos del tipo elegido. */
public class DialogoProceso extends DialogoFormulario {

    private static final int MAX_VALOR = 1_000_000;
    private static final String AUTOMATICO = "Automático";

    private final JTextField nombre = new JTextField(12);
    private final JComboBox<TipoProceso> tipo = new JComboBox<>(TipoProceso.values());
    private final JTextField prioridad = new JTextField(12);
    private final JTextField memoria = new JTextField(12);
    private final JTextField deadline = new JTextField(12);
    private final JComboBox<String> computador = new JComboBox<>();
    private final JTextField instrucciones = new JTextField(12);
    private final JTextField instruccionesIO = new JTextField(12);
    private final JTextField cadaCiclosES = new JTextField(12);
    private final JTextField duracionES = new JTextField(12);
    private final JComboBox<String> buffer = new JComboBox<>();
    private final JTextField ciclosPorElemento = new JTextField(12);
    private final JTextField elementos = new JTextField(12);
    private final CardLayout cartas = new CardLayout();
    private final JPanel especificos = new JPanel(cartas);

    /** Arma el formulario sobre el clúster indicado. */
    public DialogoProceso(Window dueno, Cluster cluster) {
        super(dueno, cluster, "Crear proceso");
        computador.addItem(AUTOMATICO);
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            computador.addItem(cluster.getComputador(i).getNombre());
        }
        tipo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice, boolean seleccionado,
                    boolean enfoque) {
                return super.getListCellRendererComponent(lista, valor instanceof TipoProceso t ? t.getNombre() : valor,
                        indice, seleccionado, enfoque);
            }
        });
        prioridad.setToolTipText(FabricaProcesos.PRIORIDAD_MAXIMA + " (más importante) a " + FabricaProcesos.PRIORIDAD_MINIMA);

        JPanel cpu = grilla();
        fila(cpu, 0, "Instrucciones:", instrucciones);
        JPanel io = grilla();
        fila(io, 0, "Instrucciones:", instruccionesIO);
        fila(io, 1, "E/S cada (instr.):", cadaCiclosES);
        fila(io, 2, "Duración E/S (ciclos):", duracionES);
        JPanel conBuffer = grilla();
        fila(conBuffer, 0, "Buffer:", buffer);
        fila(conBuffer, 1, "Ciclos por elemento:", ciclosPorElemento);
        fila(conBuffer, 2, "Elementos:", elementos);
        especificos.add(cpu, TipoProceso.CPU_BOUND.name());
        especificos.add(io, TipoProceso.IO_BOUND.name());
        especificos.add(conBuffer, "BUFFER");

        JPanel campos = new JPanel(new BorderLayout(0, 6));
        JPanel comunes = grilla();
        fila(comunes, 0, "Nombre:", nombre);
        fila(comunes, 1, "Tipo:", tipo);
        fila(comunes, 2, "Prioridad:", prioridad);
        fila(comunes, 3, "Memoria:", memoria);
        fila(comunes, 4, "Deadline (ciclos):", deadline);
        fila(comunes, 5, "Computador:", computador);
        campos.add(comunes, BorderLayout.NORTH);
        campos.add(especificos, BorderLayout.CENTER);
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

    /** Muestra solo los campos del tipo elegido. */
    private void mostrarTipo() {
        TipoProceso elegido = (TipoProceso) tipo.getSelectedItem();
        cartas.show(especificos, elegido.usaBuffer() ? "BUFFER" : elegido.name());
        if (elegido.usaBuffer()) {
            cargarBuffers();
        }
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
        Validador.ResultadoEntero prio = Validador.entero(prioridad.getText(), "Prioridad",
                FabricaProcesos.PRIORIDAD_MAXIMA, FabricaProcesos.PRIORIDAD_MINIMA);
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
            revisar = new Validador.ResultadoEntero[] {prio, mem, dead, instr};
        } else if (elegido == TipoProceso.IO_BOUND) {
            instr = Validador.entero(instruccionesIO.getText(), "Instrucciones", 1, MAX_VALOR);
            cada = Validador.entero(cadaCiclosES.getText(), "E/S cada", 1, MAX_VALOR);
            dur = Validador.entero(duracionES.getText(), "Duración E/S", 1, MAX_VALOR);
            revisar = new Validador.ResultadoEntero[] {prio, mem, dead, instr, cada, dur};
        } else {
            ciclos = Validador.entero(ciclosPorElemento.getText(), "Ciclos por elemento", 1, MAX_VALOR);
            elem = Validador.entero(elementos.getText(), "Elementos", 1, MAX_VALOR);
            revisar = new Validador.ResultadoEntero[] {prio, mem, dead, ciclos, elem};
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
            case CPU_BOUND -> DatosProceso.cpuBound(nombreProceso, prio.valor(), mem.valor(), dead.valor(), instr.valor(), destino);
            case IO_BOUND -> DatosProceso.ioBound(nombreProceso, prio.valor(), mem.valor(), dead.valor(), instr.valor(),
                    cada.valor(), dur.valor(), destino);
            case PRODUCTOR -> DatosProceso.productor(nombreProceso, prio.valor(), mem.valor(), dead.valor(),
                    (String) buffer.getSelectedItem(), ciclos.valor(), elem.valor(), destino);
            case CONSUMIDOR -> DatosProceso.consumidor(nombreProceso, prio.valor(), mem.valor(), dead.valor(),
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
