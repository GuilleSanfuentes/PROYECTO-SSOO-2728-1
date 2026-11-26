package avilaos.vista.gui;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Ventana de errores de carga: lista cada entrada apartada de los JSON con su motivo; la simulación no se detiene. */
public final class DialogoProblemas extends JDialog {

    private DialogoProblemas(Window dueno, String titulo, ListaEnlazada<String> problemas) {
        super(dueno, titulo, ModalityType.MODELESS);
        JLabel encabezado = new JLabel(problemas.tamano() + (problemas.tamano() == 1 ? " observación" : " observaciones")
                + " al cargar los JSON. Lo que se apartó no se cargó; el resto sí y la simulación sigue.");
        encabezado.setFont(encabezado.getFont().deriveFont(Font.BOLD));
        JTextArea texto = new JTextArea(texto(problemas));
        texto.setEditable(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setForeground(ColoresEstado.ROJO.darker());
        JScrollPane scroll = new JScrollPane(texto);
        scroll.setPreferredSize(new Dimension(620, 280));
        JButton cerrar = new BotonSutil("Entendido");
        cerrar.addActionListener(e -> dispose());
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sur.add(cerrar);
        JPanel contenido = new JPanel(new BorderLayout(0, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(scroll, BorderLayout.CENTER);
        contenido.add(sur, BorderLayout.SOUTH);
        setContentPane(contenido);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(dueno);
    }

    /** Muestra la ventana solo si hay problemas que reportar. */
    public static void mostrarSiHay(Window dueno, String titulo, ListaEnlazada<String> problemas) {
        if (problemas != null && !problemas.estaVacia()) {
            new DialogoProblemas(dueno, titulo, problemas).setVisible(true);
        }
    }

    /** Un problema por línea, numerados. */
    static String texto(ListaEnlazada<String> problemas) {
        StringBuilder sb = new StringBuilder();
        Nodo<String> nodo = problemas.getCabeza();
        for (int i = 1; nodo != null; i++) {
            sb.append(i).append(". ").append(nodo.getDato()).append('\n');
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }
}
