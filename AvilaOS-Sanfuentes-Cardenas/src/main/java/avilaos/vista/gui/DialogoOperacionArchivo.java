package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.enums.Permiso;
import avilaos.so.DatosProcesoArchivo;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formulario que crea el proceso de E/S de una opción del clic derecho: datos del proceso y, si hace falta, nombre, tamaño y permiso. */
public class DialogoOperacionArchivo extends DialogoFormulario {

    public static final int DEF_MEMORIA = 16;
    public static final int DEF_DEADLINE = 500;
    public static final int DEF_INSTRUCCIONES = 2;
    public static final int MAX_DEADLINE = 1_000_000;
    public static final int MAX_INSTRUCCIONES = 10_000;

    /** Resultado de armar los datos: el proceso listo o el motivo exacto del rechazo. */
    public record Armado(DatosProcesoArchivo datos, String motivo) {

        /** Indica si los datos son válidos. */
        public boolean valido() {
            return datos != null;
        }
    }

    private final FuenteUsuario fuente;
    private final JLabel operacion = new JLabel();
    private final JTextField nombreProceso = new CampoTexto(16);
    private final JTextField memoria = new CampoTexto(6);
    private final JTextField deadline = new CampoTexto(7);
    private final JTextField instrucciones = new CampoTexto(5);
    private final JComboBox<Integer> prioridad = new JComboBox<>();
    private final JTextField nombreNuevo = new CampoTexto(16);
    private final JTextField bloques = new CampoTexto(5);
    private final JComboBox<Permiso> permiso = new JComboBox<>(Permiso.values());
    private final JComboBox<String> comboDueno = new JComboBox<>();
    private final JLabel etiquetaDueno;
    private final JLabel etiquetaNombre;
    private final JLabel etiquetaBloques;
    private final JLabel etiquetaPermiso;
    private AccionArchivo accion;
    private InfoEntrada entrada;

    /** Arma el formulario vacío; se prepara para cada opción con preparar. */
    public DialogoOperacionArchivo(Window dueno, Cluster cluster, FuenteUsuario fuente) {
        super(dueno, cluster, "Proceso de E/S sobre el sistema de archivos");
        this.fuente = fuente;
        for (int p = FabricaProcesos.PRIORIDAD_MAXIMA; p <= FabricaProcesos.PRIORIDAD_MINIMA; p++) {
            prioridad.addItem(p);
        }
        FiltroEntrada.nombre(nombreProceso);
        FiltroEntrada.soloDigitos(memoria, 7);
        FiltroEntrada.soloDigitos(deadline, 7);
        FiltroEntrada.soloDigitos(instrucciones, 5);
        FiltroEntrada.nombreArchivo(nombreNuevo);
        FiltroEntrada.soloDigitos(bloques, 5);
        JPanel campos = grilla();
        int f = 0;
        fila(campos, f++, "Operación", operacion);
        fila(campos, f++, "Nombre del proceso", nombreProceso);
        fila(campos, f++, "Memoria (unidades de RAM)", memoria);
        fila(campos, f++, "Deadline (ciclos)", deadline);
        fila(campos, f++, "Instrucciones de cálculo", instrucciones);
        fila(campos, f++, "Prioridad (1 = más importante)", prioridad);
        etiquetaNombre = fila(campos, f++, "Nombre", nombreNuevo);
        etiquetaBloques = fila(campos, f++, "Tamaño (bloques)", bloques);
        etiquetaPermiso = fila(campos, f++, "Permiso", permiso);
        etiquetaDueno = fila(campos, f, "Dueño (lo elige el administrador)", comboDueno);
        comboDueno.setToolTipText("El proceso actúa con permisos de administrador y lo creado queda a nombre de este dueño");
        permiso.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> lista, Object valor, int indice,
                    boolean seleccionado, boolean enfoque) {
                return super.getListCellRendererComponent(lista, valor instanceof Permiso p ? p.getNombre() : valor, indice,
                        seleccionado, enfoque);
            }
        });
        limpiarCampos();
        armar(campos, "Crear proceso");
    }

    /** Prepara el formulario para la opción y el elemento elegidos y lo muestra. */
    public void preparar(AccionArchivo nueva, InfoEntrada sobre) {
        accion = nueva;
        entrada = sobre;
        setTitle("Proceso de E/S: " + nueva.getTexto().replace("...", ""));
        operacion.setText(nueva.getOperacion().getNombre() + (nueva.creaAlgo() ? " en " : " ") + sobre.ruta());
        nombreProceso.setText(nombreSugerido(nueva, sobre));
        boolean pideNombre = nueva.creaAlgo() || nueva == AccionArchivo.RENOMBRAR;
        etiquetaNombre.setText(nueva == AccionArchivo.RENOMBRAR ? "Nombre nuevo" : "Nombre");
        etiquetaNombre.setVisible(pideNombre);
        nombreNuevo.setVisible(pideNombre);
        nombreNuevo.setText(nueva == AccionArchivo.RENOMBRAR ? sobre.nombre() : "");
        etiquetaBloques.setVisible(nueva == AccionArchivo.CREAR_ARCHIVO);
        bloques.setVisible(nueva == AccionArchivo.CREAR_ARCHIVO);
        etiquetaPermiso.setVisible(nueva.creaAlgo());
        permiso.setVisible(nueva.creaAlgo());
        boolean eligeDueno = nueva.creaAlgo() && fuente.actual().esAdmin();
        etiquetaDueno.setVisible(eligeDueno);
        comboDueno.setVisible(eligeDueno);
        comboDueno.removeAllItems();
        comboDueno.addItem(Usuario.NOMBRE_ADMIN);
        avilaos.so.archivos.SistemaArchivos fs = cluster.getComputador(0).getSistemaArchivos();
        avilaos.estructuras.Nodo<String> d = fs == null ? null : fs.duenos().getCabeza();
        while (d != null) {
            if (!Usuario.NOMBRE_ADMIN.equals(d.getDato())) {
                comboDueno.addItem(d.getDato());
            }
            d = d.getSiguiente();
        }
        reajustar();
        setVisible(true);
        toFront();
    }

    @Override
    protected void crear() {
        if (!simulacionAdmite()) {
            return;
        }
        Usuario usuario = accion.creaAlgo() ? actuante(fuente.actual(), (String) comboDueno.getSelectedItem()) : fuente.actual();
        int libres = cluster.getComputador(0).getDisco() == null ? 0 : cluster.getComputador(0).getDisco().contarLibres(false);
        Armado armado = armar(accion, entrada, nombreProceso.getText(), memoria.getText(), deadline.getText(),
                instrucciones.getText(), (Integer) prioridad.getSelectedItem(), nombreNuevo.getText(), bloques.getText(),
                cluster.getComputador(0).getRam().getCapacidad(), libres);
        if (!armado.valido()) {
            error(armado.motivo());
            return;
        }
        Permiso elegido = accion.creaAlgo() ? (Permiso) permiso.getSelectedItem() : Permiso.PUBLICO;
        String motivo = cluster.validarProcesoArchivo(armado.datos(), usuario, elegido);
        if (motivo != null) {
            error(motivo);
            return;
        }
        PCB pcb = cluster.crearProcesoArchivo(armado.datos(), usuario, elegido);
        if (pcb == null) {
            error("No se pudo crear el proceso");
            return;
        }
        exito("Proceso " + pcb.getEtiqueta() + " creado: hará " + armado.datos().operacion().getNombre().toLowerCase()
                + " sobre " + armado.datos().ruta() + " como " + usuario.nombre());
    }

    @Override
    protected void limpiarCampos() {
        memoria.setText(String.valueOf(DEF_MEMORIA));
        deadline.setText(String.valueOf(DEF_DEADLINE));
        instrucciones.setText(String.valueOf(DEF_INSTRUCCIONES));
        prioridad.setSelectedItem(FabricaProcesos.PRIORIDAD_MAXIMA + 4);
        bloques.setText("1");
        permiso.setSelectedItem(Permiso.PUBLICO);
    }

    /** Valida los textos del formulario y arma los datos del proceso; si algo está mal devuelve el motivo exacto. */
    public static Armado armar(AccionArchivo accion, InfoEntrada entrada, String nombreProceso, String memoria, String deadline,
            String instrucciones, int prioridad, String nombreNuevo, String bloques, int ramTotal, int libres) {
        if (accion == null || entrada == null) {
            return new Armado(null, "Falta elegir la operación y el elemento");
        }
        String nombre = nombreProceso == null ? "" : nombreProceso.trim();
        if (nombre.isEmpty()) {
            return new Armado(null, "Nombre del proceso: está vacío");
        }
        Validador.ResultadoEntero mem = Validador.entero(memoria, "Memoria", 1, ramTotal);
        if (!mem.valido()) {
            return new Armado(null, mem.motivo());
        }
        Validador.ResultadoEntero dl = Validador.entero(deadline, "Deadline", 1, MAX_DEADLINE);
        if (!dl.valido()) {
            return new Armado(null, dl.motivo());
        }
        Validador.ResultadoEntero ins = Validador.entero(instrucciones, "Instrucciones", 1, MAX_INSTRUCCIONES);
        if (!ins.valido()) {
            return new Armado(null, ins.motivo());
        }
        String nuevo = nombreNuevo == null ? "" : nombreNuevo.trim();
        if (accion.creaAlgo() || accion == AccionArchivo.RENOMBRAR) {
            String problema = SistemaArchivos.motivoNombre(nuevo);
            if (problema != null) {
                return new Armado(null, "Nombre: " + problema);
            }
        }
        int tamano = 0;
        if (accion == AccionArchivo.CREAR_ARCHIVO) {
            if (libres <= 0) {
                return new Armado(null, "No hay bloques libres en el disco para crear archivos");
            }
            Validador.ResultadoEntero bl = Validador.entero(bloques, "Tamaño (bloques)", 1, libres);
            if (!bl.valido()) {
                return new Armado(null, bl.motivo() + " (bloques libres en el disco: " + libres + ")");
            }
            tamano = bl.valor();
        }
        String ruta = accion.creaAlgo() ? hijo(entrada.ruta(), nuevo) : entrada.ruta();
        String renombre = accion == AccionArchivo.RENOMBRAR ? nuevo : null;
        return new Armado(new DatosProcesoArchivo(nombre, mem.valor(), dl.valor(), ins.valor(), ruta, accion.getOperacion(),
                tamano, prioridad, renombre), null);
    }

    /** Quién hace la creación: el administrador puede actuar a nombre de otro dueño (con sus permisos); un usuario, solo como él. */
    public static Usuario actuante(Usuario usuario, String dueno) {
        if (usuario == null || !usuario.esAdmin() || dueno == null || dueno.isBlank()) {
            return usuario;
        }
        return new Usuario(dueno.trim(), usuario.modo());
    }

    /** Ruta de un elemento nuevo dentro del directorio. */
    static String hijo(String directorio, String nombre) {
        return SistemaArchivos.RAIZ.equals(directorio) ? SistemaArchivos.RAIZ + nombre : directorio + SistemaArchivos.RAIZ + nombre;
    }

    /** Nombre corto para el proceso: operación y elemento ("Leer_notas.txt"), cabe en el campo. */
    public static String nombreSugerido(AccionArchivo accion, InfoEntrada sobre) {
        String base = switch (accion) {
            case LEER -> "Leer";
            case ACTUALIZAR -> "Actualizar";
            case RENOMBRAR -> "Renombrar";
            case ELIMINAR -> "Eliminar";
            case CREAR_ARCHIVO, CREAR_DIRECTORIO -> "Crear";
        };
        String objeto = SistemaArchivos.RAIZ.equals(sobre.ruta()) ? "raiz" : sobre.nombre();
        StringBuilder limpio = new StringBuilder();
        for (char c : (base + "_" + objeto).toCharArray()) {
            if (FiltroEntrada.aceptable(c, FiltroEntrada.Tipo.NOMBRE)) {
                limpio.append(c);
            }
        }
        return limpio.length() > FiltroEntrada.LARGO_NOMBRE ? limpio.substring(0, FiltroEntrada.LARGO_NOMBRE) : limpio.toString();
    }
}
