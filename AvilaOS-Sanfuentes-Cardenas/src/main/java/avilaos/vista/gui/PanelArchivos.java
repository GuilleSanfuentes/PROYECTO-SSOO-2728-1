package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Sistema de archivos: JTree con clic derecho (cada opción crea un proceso de E/S) y la ficha del elemento elegido. */
public class PanelArchivos extends JPanel {

    private final Cluster cluster;
    private final FuenteUsuario fuente;
    private final DefaultMutableTreeNode raiz = new DefaultMutableTreeNode();
    private final DefaultTreeModel modelo = new DefaultTreeModel(raiz);
    private final JTree arbol = new JTree(modelo);
    private final JLabel nombre = new JLabel("-");
    private final JLabel ruta = new JLabel("-");
    private final JLabel tipo = new JLabel("-");
    private final JLabel tamano = new JLabel("-");
    private final JLabel dueno = new JLabel("-");
    private final JLabel permisos = new JLabel("-");
    private final JLabel primerBloque = new JLabel("-");
    private final JLabel creador = new JLabel("-");
    private final JLabel ultima = new JLabel("-");
    private DialogoOperacionArchivo dialogo;
    private FotoArchivos foto;
    private String firma = "";

    /** Arma el panel vacío; el árbol se llena al recibir la primera foto. */
    public PanelArchivos(Cluster cluster, FuenteUsuario fuente) {
        super(new BorderLayout(0, 6));
        this.cluster = cluster;
        this.fuente = fuente;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(208, 213, 220)), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JLabel titulo = new JLabel("Sistema de archivos");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        JLabel ayuda = new JLabel("Clic derecho: crea un proceso de E/S");
        ayuda.setToolTipText("Las operaciones sobre archivos nunca las hace la interfaz: cada opción crea un proceso de E/S");
        ayuda.setForeground(new Color(108, 117, 125));
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(titulo, BorderLayout.NORTH);
        arriba.add(ayuda, BorderLayout.SOUTH);
        add(arriba, BorderLayout.NORTH);

        arbol.setRootVisible(true);
        arbol.setShowsRootHandles(true);
        arbol.setCellRenderer(new Renderizador());
        ToolTipManager.sharedInstance().registerComponent(arbol);
        arbol.addTreeSelectionListener(e -> mostrarFicha(seleccionada()));
        arbol.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                menu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                menu(e);
            }
        });
        add(new JScrollPane(arbol), BorderLayout.CENTER);
        add(ficha(), BorderLayout.SOUTH);
    }

    /** Dibuja la foto: reconstruye el árbol solo si cambió su estructura y conserva lo seleccionado y lo expandido. */
    public void mostrar(FotoArchivos nueva) {
        if (nueva == null) {
            return;
        }
        foto = nueva;
        String nuevaFirma = nueva.firma();
        if (!nuevaFirma.equals(firma)) {
            firma = nuevaFirma;
            String elegida = seleccionada() == null ? null : seleccionada().ruta();
            reconstruir(nueva);
            seleccionar(elegida);
        }
        InfoEntrada actual = seleccionada();
        mostrarFicha(actual == null ? null : nueva.buscar(actual.ruta()));
    }

    /** Vuelve a armar los nodos en preorden usando la profundidad de cada entrada; deja todo expandido. */
    private void reconstruir(FotoArchivos f) {
        raiz.removeAllChildren();
        DefaultMutableTreeNode[] porNivel = new DefaultMutableTreeNode[64];
        for (InfoEntrada e : f.entradas()) {
            if (e.profundidad() == 0) {
                raiz.setUserObject(e);
                porNivel[0] = raiz;
                continue;
            }
            DefaultMutableTreeNode nodo = new DefaultMutableTreeNode(e, e.esDirectorio());
            int nivel = Math.min(e.profundidad(), porNivel.length - 1);
            DefaultMutableTreeNode padre = porNivel[nivel - 1] == null ? raiz : porNivel[nivel - 1];
            padre.add(nodo);
            porNivel[nivel] = nodo;
        }
        modelo.reload();
        for (int i = 0; i < arbol.getRowCount(); i++) {
            arbol.expandRow(i);
        }
    }

    /** Selecciona el nodo con esa ruta, si existe. */
    private void seleccionar(String rutaBuscada) {
        if (rutaBuscada == null) {
            return;
        }
        for (int i = 0; i < arbol.getRowCount(); i++) {
            TreePath camino = arbol.getPathForRow(i);
            if (entradaDe(camino) != null && entradaDe(camino).ruta().equals(rutaBuscada)) {
                arbol.setSelectionPath(camino);
                return;
            }
        }
    }

    /** Abre el menú contextual sobre el elemento bajo el ratón, con las opciones permitidas según el usuario activo. */
    private void menu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath camino = arbol.getPathForLocation(e.getX(), e.getY());
        InfoEntrada entrada = entradaDe(camino);
        if (entrada == null) {
            return;
        }
        arbol.setSelectionPath(camino);
        Usuario usuario = fuente.actual();
        SistemaArchivos fs = cluster.getComputador(0).getSistemaArchivos();
        JPopupMenu menu = new JPopupMenu();
        JLabel quien = new JLabel("  " + entrada.ruta() + "  ·  como " + usuario.nombre() + " (" + usuario.modo().getNombre() + ")");
        quien.setFont(quien.getFont().deriveFont(Font.BOLD));
        quien.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        menu.add(quien);
        menu.addSeparator();
        for (OpcionesArchivo.Opcion opcion : OpcionesArchivo.para(entrada, usuario, fs)) {
            JMenuItem item = new JMenuItem(opcion.habilitada() ? opcion.accion().getTexto()
                    : "<html>" + opcion.accion().getTexto() + " <font color='#9aa0a6'>(no permitido: " + opcion.motivo() + ")</font></html>");
            item.setEnabled(opcion.habilitada());
            item.setToolTipText(opcion.habilitada() ? "Crea un proceso de E/S que hará: " + opcion.accion().getOperacion().getNombre()
                    : "No permitido: " + opcion.motivo());
            item.addActionListener(ev -> abrirDialogo(opcion.accion(), entrada));
            menu.add(item);
        }
        menu.show(arbol, e.getX(), e.getY());
    }

    private void abrirDialogo(AccionArchivo accion, InfoEntrada entrada) {
        if (dialogo == null) {
            dialogo = new DialogoOperacionArchivo(SwingUtilities.getWindowAncestor(this), cluster, fuente);
        }
        dialogo.preparar(accion, entrada);
    }

    private InfoEntrada seleccionada() {
        return entradaDe(arbol.getSelectionPath());
    }

    private static InfoEntrada entradaDe(TreePath camino) {
        if (camino == null || !(camino.getLastPathComponent() instanceof DefaultMutableTreeNode nodo)) {
            return null;
        }
        return nodo.getUserObject() instanceof InfoEntrada e ? e : null;
    }

    /** Ficha del elemento elegido: lo que pide el enunciado (nombre, tamaño, dueño, permisos y primer bloque) y algo más. */
    private void mostrarFicha(InfoEntrada e) {
        if (e == null) {
            for (JLabel l : new JLabel[]{nombre, ruta, tipo, tamano, dueno, permisos, primerBloque, creador, ultima}) {
                l.setText("-");
            }
            return;
        }
        nombre.setText(e.nombre());
        ruta.setText(e.ruta());
        tipo.setText(e.tipo().getNombre());
        tamano.setText(e.esDirectorio() ? "(directorio)" : e.bloques() + (e.bloques() == 1 ? " bloque" : " bloques"));
        dueno.setText(e.dueno());
        permisos.setText(e.permiso().getNombre());
        primerBloque.setText(e.esDirectorio() ? "-" : String.valueOf(e.primerBloque()));
        creador.setText(e.creador());
        ultima.setText(e.ultimaOperacion());
    }

    private JPanel ficha() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Elemento seleccionado"));
        int f = 0;
        f = filaFicha(panel, f, "Nombre", nombre);
        f = filaFicha(panel, f, "Ruta", ruta);
        f = filaFicha(panel, f, "Tipo", tipo);
        f = filaFicha(panel, f, "Tamaño", tamano);
        f = filaFicha(panel, f, "Dueño", dueno);
        f = filaFicha(panel, f, "Permisos", permisos);
        f = filaFicha(panel, f, "Primer bloque", primerBloque);
        f = filaFicha(panel, f, "Creado por", creador);
        filaFicha(panel, f, "Última operación", ultima);
        return panel;
    }

    private static int filaFicha(JPanel panel, int fila, String etiqueta, JLabel valor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 4, 1, 6);
        JLabel nombreCampo = new JLabel(etiqueta);
        nombreCampo.setForeground(new Color(108, 117, 125));
        panel.add(nombreCampo, c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(valor, c);
        return fila + 1;
    }

    /** Nodo con su nombre, su tamaño y un cuadro del color del archivo; tooltip con dueño y permisos. */
    private static final class Renderizador extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree arbol, Object valor, boolean seleccionado, boolean expandido,
                boolean hoja, int fila, boolean foco) {
            super.getTreeCellRendererComponent(arbol, valor, seleccionado, expandido, hoja, fila, foco);
            if (valor instanceof DefaultMutableTreeNode nodo && nodo.getUserObject() instanceof InfoEntrada e) {
                if (e.esDirectorio()) {
                    setText(e.ruta().equals(SistemaArchivos.RAIZ) ? "/ (raíz)" : e.nombre());
                    setIcon(expandido ? getOpenIcon() : getClosedIcon());
                } else {
                    setText("<html><font color='" + hex(ColoresArchivo.de(e.id())) + "'>■</font> " + e.nombre()
                            + " <font color='#6c757d'>(" + e.bloques() + " bl.)</font></html>");
                    setIcon(getLeafIcon());
                }
                setToolTipText(e.ruta() + " · dueño " + e.dueno() + " · " + e.permiso().getNombre().toLowerCase()
                        + (e.esDirectorio() ? "" : " · primer bloque " + e.primerBloque()));
            }
            return this;
        }

        private static String hex(Color c) {
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }
    }
}
