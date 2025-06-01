import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// ----------------------
// Localization – Texte
// ----------------------
class Localization {
    public static String get(String key) {
        String lang = Main.LANGUAGE;
        HashMap<String, String> de = new HashMap<>();
        de.put("legendTitle", "Legende:");
        de.put("legendArrowLeft", "←: Vorheriger Schritt");
        de.put("legendArrowRight", "→: Nächster Schritt");
        de.put("legendEnterNew", "Enter: Neuer Baum (mit Enter bestätigen)");
        de.put("legendEnterAppend", "Shift+Enter: Zahlen bearbeiten");
        de.put("legendEsc", "Esc: Beenden");
        de.put("confirmNewTitle", "Baum erstellen?");
        de.put("confirmNewMessage", "Einen neuen Baum erstellen? (Alter wird gelöscht)");
        de.put("modifyInputTitle", "Zahlen bearbeiten");
        de.put("modifyInputMessage", "Bearbeite die Zahlen (durch Komma getrennt):");
        de.put("btnShowIntermediate", "Zwischenschritte");
        de.put("intermediateTitle", "Zwischenschritte");
        de.put("intermediateLegend", "Pfeiltasten: navigieren, Esc: schließen");
        de.put("fixedRules",
                """
                        Feste Regeln:
                        1. Jeder Knoten ist entweder rot oder schwarz.
                        2. Die Wurzel ist immer schwarz.
                        3. Alle Blätter sind schwarz.
                        4. Wenn ein Knoten rot ist, müssen seine Kinder schwarz sein.
                        5. Jeder Pfad von der Wurzel zu einem Blatt enthält die gleiche Anzahl schwarzer Knoten.
                        Fall 1: Der Onkel ist rot → Parent und Onkel werden schwarz, Grandparent wird rot.
                        Fall 2: Der Onkel ist schwarz, das eingefügte Kind liegt "zur Mitte hin" → Rotation um den Parent.
                        Fall 3: Der Onkel ist schwarz, das eingefügte Kind liegt "außen" → Rotation um den Grandparent und Farbänderung."""
        );

        HashMap<String, String> en = new HashMap<>();
        en.put("legendTitle", "Legend:");
        en.put("legendArrowLeft", "←: Previous step");
        en.put("legendArrowRight", "→: Next step");
        en.put("legendEnterNew", "Enter: New tree (confirm with Enter)");
        en.put("legendEnterAppend", "Shift+Enter: Edit numbers");
        en.put("legendEsc", "Esc: Exit");
        en.put("confirmNewTitle", "Create new tree?");
        en.put("confirmNewMessage", "Create a new tree? (old one will be deleted)");
        en.put("modifyInputTitle", "Edit numbers");
        en.put("modifyInputMessage", "Edit numbers (comma separated):");
        en.put("btnShowIntermediate", "Intermediate Steps");
        en.put("intermediateTitle", "Intermediate Steps");
        en.put("intermediateLegend", "Arrow keys: navigate, Esc: close");
        en.put("fixedRules",
                """
                        Fixed Rules:
                        1. Every node is either red or black.
                        2. The root is always black.
                        3. All leaves are black.
                        4. If a node is red, its children must be black.
                        5. Every path from the root to a leaf contains the same number of black nodes.
                        Case 1: If the uncle is red → Parent and uncle become black, grandparent becomes red.
                        Case 2: If the uncle is black and the inserted node lies in the middle → Rotate around the parent.
                        Case 3: If the uncle is black and the inserted node lies on the outside → Rotate around the grandparent and recolor."""
        );
        return lang.equals("EN") ? en.get(key) : de.get(key);
    }
}

// ----------------------
// Baum-Klassen
// ----------------------
class Node {
    int key;
    boolean isRed;
    Node left, right, parent;

    public Node(int key) {
        this.key = key;
        this.isRed = true;
    }
}

// TreeState speichert den Deep Copy des Baumzustandes,
// die aktiven Regel-Schritte (z.B. "Schritt 1: Fall 1" bzw. "Step 1: Case 1")
// und die gesammelten Zwischenzustände des aktuellen Einfügeschritts.
class TreeState {
    Node treeClone;
    List<String> ruleMessages;
    List<Node> intermediateStates;
    List<String> intermediateRules;

    public TreeState(Node treeClone, List<String> ruleMessages, List<Node> intermediateStates, List<String> intermediateRules) {
        this.treeClone = treeClone;
        this.ruleMessages = ruleMessages;
        this.intermediateStates = intermediateStates;
        this.intermediateRules = intermediateRules;
    }
}

class RedBlackTree {
    private Node root;
    private TreeVisualizer visualizer;
    private List<TreeState> steps = new ArrayList<>();
    private int currentStep = 0;

    public RedBlackTree(TreeVisualizer visualizer) {
        this.visualizer = visualizer;
    }

    public void insert(int key) {
        List<String> currentRules = new ArrayList<>();
        List<Node> interStates = new ArrayList<>();
        List<String> interRuleList = new ArrayList<>();

        Node newNode = new Node(key);
        root = insertRecursive(root, newNode);
        fixViolation(newNode, currentRules, interStates, interRuleList);
        root.isRed = false;

        steps.add(new TreeState(cloneTree(root), currentRules, interStates, interRuleList));
        visualizer.updateStep(currentStep);
        visualizer.repaint();

        visualizer.setShowIntermediateButton(interStates.size() >= 2, interStates, interRuleList);
    }

    private Node insertRecursive(Node root, Node newNode) {
        if (root == null) return newNode;
        if (newNode.key < root.key) {
            root.left = insertRecursive(root.left, newNode);
            root.left.parent = root;
        } else {
            root.right = insertRecursive(root.right, newNode);
            root.right.parent = root;
        }
        return root;
    }

    // fixViolation führt die nötigen Transformationen durch und sammelt jeden Zwischenzustand.
    // Abhängig von der Sprache wird "Schritt X: Fall Y" (DE) oder "Step X: Case Y" (EN) in currentRules aufgenommen.
    private void fixViolation(Node node, List<String> currentRules, List<Node> interStates, List<String> interRuleList) {
        String stepPrefix = Main.LANGUAGE.equals("DE") ? "Schritt" : "Step";
        String case1 = Main.LANGUAGE.equals("DE") ? "Fall 1" : "Case 1";
        String case2 = Main.LANGUAGE.equals("DE") ? "Fall 2" : "Case 2";
        String case3 = Main.LANGUAGE.equals("DE") ? "Fall 3" : "Case 3";

        while (node != null && node.parent != null && node.parent.isRed) {
            Node grandparent = node.parent.parent;
            if (grandparent == null) break;
            Node uncle = (node.parent == grandparent.left) ? grandparent.right : grandparent.left;

            if (uncle != null && uncle.isRed) {
                currentRules.add(stepPrefix + " " + (currentRules.size() + 1) + ": " + case1);
                interRuleList.add(case1);
                node.parent.isRed = false;
                uncle.isRed = false;
                grandparent.isRed = true;
                interStates.add(cloneTree(root));
                node = grandparent;
            } else {
                if (node == node.parent.right && node.parent == grandparent.left) {
                    currentRules.add(stepPrefix + " " + (currentRules.size() + 1) + ": " + case2);
                    interRuleList.add(case2);
                    rotateLeft(node.parent);
                    interStates.add(cloneTree(root));
                    node = node.left;
                } else if (node == node.parent.left && node.parent == grandparent.right) {
                    currentRules.add(stepPrefix + " " + (currentRules.size() + 1) + ": " + case2);
                    interRuleList.add(case2);
                    rotateRight(node.parent);
                    interStates.add(cloneTree(root));
                    node = node.right;
                } else {
                    currentRules.add(stepPrefix + " " + (currentRules.size() + 1) + ": " + case3);
                    interRuleList.add(case3);
                    node.parent.isRed = false;
                    grandparent.isRed = true;
                    if (node == node.parent.left) rotateRight(grandparent);
                    else rotateLeft(grandparent);
                    interStates.add(cloneTree(root));
                    break;
                }
            }
        }
        root.isRed = false;
        if (currentRules.size() >= 2) interStates.add(cloneTree(root));
    }

    private void rotateLeft(Node node) {
        Node temp = node.right;
        node.right = temp.left;
        if (temp.left != null) temp.left.parent = node;
        temp.parent = node.parent;
        if (node.parent == null) root = temp;
        else if (node == node.parent.left) node.parent.left = temp;
        else node.parent.right = temp;
        temp.left = node;
        node.parent = temp;
    }

    private void rotateRight(Node node) {
        Node temp = node.left;
        node.left = temp.right;
        if (temp.right != null) temp.right.parent = node;
        temp.parent = node.parent;
        if (node.parent == null) root = temp;
        else if (node == node.parent.right) node.parent.right = temp;
        else node.parent.left = temp;
        temp.right = node;
        node.parent = temp;
    }

    private Node cloneTree(Node node) {
        if (node == null) return null;
        Node newNode = new Node(node.key);
        newNode.isRed = node.isRed;
        newNode.left = cloneTree(node.left);
        newNode.right = cloneTree(node.right);
        return newNode;
    }

    public void changeStep(int direction) {
        if (direction == 1 && currentStep < steps.size() - 1) currentStep++;
        if (direction == -1 && currentStep > 0) currentStep--;
        TreeState current = steps.get(currentStep);
        visualizer.updateStep(currentStep);
        visualizer.repaint();
        visualizer.setShowIntermediateButton(current.intermediateStates.size() >= 2, current.intermediateStates, current.intermediateRules);
    }

    public Node getCurrentTree() {
        return (!steps.isEmpty()) ? steps.get(currentStep).treeClone : null;
    }

    public List<String> getCurrentRuleMessages() {
        return (!steps.isEmpty()) ? steps.get(currentStep).ruleMessages : new ArrayList<>();
    }

}

// ----------------------
// GUI – TreeVisualizer
// ----------------------
class TreeVisualizer extends JPanel {
    private RedBlackTree tree;
    private int stepNumber = 0;
    private final int nodeRadius = 15;
    private JButton btnIntermediate = new JButton(Localization.get("btnShowIntermediate"));
    private List<Node> currentIntermediateStates = new ArrayList<>();
    private List<String> currentRuleMessages = new ArrayList<>();

    public TreeVisualizer(RedBlackTree tree) {
        this.tree = tree;
        setBackground(new Color(40, 40, 40));
        setLayout(null); // Absolute Positionierung

        btnIntermediate.setFont(btnIntermediate.getFont().deriveFont(10f));
        btnIntermediate.addActionListener(e -> {
            IntermediateStepsWindow win = new IntermediateStepsWindow(
                    SwingUtilities.getWindowAncestor(this),
                    currentIntermediateStates,
                    currentRuleMessages,
                    Localization.get("intermediateTitle")
            );
            if (btnIntermediate.isEnabled()) {
                win.setVisible(true);
            }
        });
        btnIntermediate.setVisible(true);
        add(btnIntermediate);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        btnIntermediate.setBounds(getWidth() / 2 - 60, getHeight() - 120, 120, 25);
    }

    public void setTree(RedBlackTree tree) {
        this.tree = tree;
        repaint();
    }

    public void updateStep(int step) {
        this.stepNumber = step;
    }

    public void setShowIntermediateButton(boolean show, List<Node> intermediateStates, List<String> ruleMessages){
        this.currentIntermediateStates = intermediateStates;
        this.currentRuleMessages = ruleMessages;
        btnIntermediate.setVisible(show && intermediateStates.size() >= 2);
        btnIntermediate.setEnabled(intermediateStates.size() >= 2);
        invalidate();
        revalidate();
        repaint();

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (tree != null) {
            drawTree(g, tree.getCurrentTree(), getWidth() / 2, 80, getWidth() / 4);
        }

        Font orig = g.getFont();
        Font small = orig.deriveFont(Font.PLAIN, 10f);
        g.setFont(small);

        String fixedRulesStr = Localization.get("fixedRules");
        String[] fixedRulesLines = fixedRulesStr.split("\n");
        int fixedRulesX = 20;
        int fixedRulesY = getHeight() - (fixedRulesLines.length * 12 + 20);
        List<String> activeRules = tree.getCurrentRuleMessages();

        for (String line : fixedRulesLines) {
            boolean isCaseLine = Main.LANGUAGE.equals("DE") ? line.contains("Fall") : line.contains("Case");
            if (isCaseLine) {
                String target = line.matches(".*Fall 1.*|.*Case 1.*") ? (Main.LANGUAGE.equals("DE") ? "Fall 1" : "Case 1") :
                        line.matches(".*Fall 2.*|.*Case 2.*") ? (Main.LANGUAGE.equals("DE") ? "Fall 2" : "Case 2") :
                                (Main.LANGUAGE.equals("DE") ? "Fall 3" : "Case 3");

                String appended = "";
                for (String rule : activeRules) {
                    String[] parts = rule.split(":", 2);
                    if (parts.length >= 2) {
                        String stepPart = parts[0].trim();
                        String casePart = parts[1].trim();
                        if (casePart.equalsIgnoreCase(target)) {
                            appended = stepPart;
                            break;
                        }
                    }
                }
                if (!appended.isEmpty()) {
                    line = line + " (" + appended + ")";
                }
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.LIGHT_GRAY);
            }
            g.drawString(line, fixedRulesX, fixedRulesY);
            fixedRulesY += 12;
        }

        String[] legendLines = new String[]{
                Localization.get("legendTitle"),
                Localization.get("legendArrowLeft"),
                Localization.get("legendArrowRight"),
                Localization.get("legendEnterNew"),
                Localization.get("legendEnterAppend"),
                Localization.get("legendEsc")
        };
        int legendX = getWidth() - 220;
        int legendY = getHeight() - (legendLines.length * 12 + 20);
        g.setColor(Color.LIGHT_GRAY);
        for (String l : legendLines) {
            g.drawString(l, legendX, legendY);
            legendY += 12;
        }

        g.setFont(orig);
    }

    private void drawTree(Graphics g, Node node, int x, int y, int xOffset) {
        if (node == null) return;
        g.setColor(node.isRed ? Color.RED : Color.BLACK);
        g.fillOval(x - nodeRadius, y - nodeRadius, nodeRadius * 2, nodeRadius * 2);
        g.setColor(Color.WHITE);
        String num = String.valueOf(node.key);
        int numWidth = g.getFontMetrics().stringWidth(num);
        int numHeight = g.getFontMetrics().getAscent();
        g.drawString(num, x - numWidth / 2, y + numHeight / 2 - 2);

        int childY = y + 40;
        if (node.left != null) {
            int childX = x - xOffset;
            drawEdge(g, x, y, childX, childY);
            drawTree(g, node.left, childX, childY, xOffset / 2);
        } else {
            int nilX = x - xOffset;
            drawEdge(g, x, y, nilX, childY);
            drawNil(g, nilX, childY);
        }

        if (node.right != null) {
            int childX = x + xOffset;
            drawEdge(g, x, y, childX, childY);
            drawTree(g, node.right, childX, childY, xOffset / 2);
        } else {
            int nilX = x + xOffset;
            drawEdge(g, x, y, nilX, childY);
            drawNil(g, nilX, childY);
        }
    }

    private void drawEdge(Graphics g, int parentX, int parentY, int childX, int childY) {
        double dx = childX - parentX;
        double dy = childY - parentY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int startX = (int)Math.round(parentX + (dx / dist * nodeRadius));
        int startY = (int)Math.round(parentY + (dy / dist * nodeRadius));
        int endX = (int)Math.round(childX - (dx / dist * nodeRadius));
        int endY = (int)Math.round(childY - (dy / dist * nodeRadius));
        g.setColor(Color.WHITE);
        g.drawLine(startX, startY, endX, endY);
    }

    private void drawNil(Graphics g, int x, int y) {
        g.setColor(Color.GRAY);
        g.fillOval(x - nodeRadius, y - nodeRadius, nodeRadius * 2, nodeRadius * 2);
        g.setColor(Color.WHITE);
        String txt = "NIL";
        int w = g.getFontMetrics().stringWidth(txt);
        int h = g.getFontMetrics().getAscent();
        g.drawString(txt, x - w / 2, y + h / 2 - 2);
    }
}

// ----------------------
// Fenster für Zwischenzustände
// ----------------------
class IntermediateStepsWindow extends JDialog {
    private List<Node> intermediateStates;
    private List<String> ruleMessages;
    private int currentIndex = 0;
    private IntermediateTreePanel treePanel;
    private JLabel lblDescription = new JLabel("", SwingConstants.CENTER);

    public IntermediateStepsWindow(Window owner, List<Node> intermediateStates, List<String> ruleMessages, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.intermediateStates = intermediateStates;
        this.ruleMessages = ruleMessages;

        setSize(800, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        treePanel = new IntermediateTreePanel(intermediateStates.get(currentIndex));
        add(treePanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(40, 40, 40));

        lblDescription.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblDescription.setForeground(Color.YELLOW);
        bottomPanel.add(lblDescription, BorderLayout.CENTER);

        JLabel lblInst = new JLabel(Localization.get("intermediateLegend"), SwingConstants.CENTER);
        lblInst.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblInst.setForeground(Color.LIGHT_GRAY);
        bottomPanel.add(lblInst, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        updateRuleLabel();

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    if (currentIndex < intermediateStates.size() - 1) {
                        currentIndex++;
                        treePanel.setTreeState(intermediateStates.get(currentIndex));
                        updateRuleLabel();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    if (currentIndex > 0) {
                        currentIndex--;
                        treePanel.setTreeState(intermediateStates.get(currentIndex));
                        updateRuleLabel();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });

        setFocusable(true);
        requestFocusInWindow();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner instanceof JFrame) {
                    ((JFrame) owner).requestFocusInWindow();
                }
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (owner instanceof JFrame) {
                    ((JFrame) owner).requestFocusInWindow();
                }
            }
        });
    }

    private void updateRuleLabel() {
        if (currentIndex < ruleMessages.size()) {
            String ruleKey = ruleMessages.get(currentIndex); // z. B. "Fall 2" oder "Case 3"
            String[] allRules = Localization.get("fixedRules").split("\n");
            for (String line : allRules) {
                if (line.contains(ruleKey)) {
                    lblDescription.setText(line);
                    return;
                }
            }
            lblDescription.setText(ruleKey); // Fallback
        } else {
            lblDescription.setText("");
        }
    }
}

// ----------------------
// Panel zum Zeichnen eines einzelnen Zwischenzustandes
// ----------------------
class IntermediateTreePanel extends JPanel {
    private Node treeState;
    private final int nodeRadius = 15;
    public IntermediateTreePanel(Node treeState) {
        this.treeState = treeState;
        setBackground(new Color(40,40,40));
    }
    public void setTreeState(Node treeState) {
        this.treeState = treeState;
        repaint();
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(treeState != null){
            drawTree(g, treeState, getWidth()/2, 80, getWidth()/4);
        }
    }
    private void drawTree(Graphics g, Node node, int x, int y, int xOffset) {
        if(node == null) return;
        g.setColor(node.isRed ? Color.RED : Color.BLACK);
        g.fillOval(x - nodeRadius, y - nodeRadius, nodeRadius*2, nodeRadius*2);
        g.setColor(Color.WHITE);
        String num = String.valueOf(node.key);
        int numWidth = g.getFontMetrics().stringWidth(num);
        int numHeight = g.getFontMetrics().getAscent();
        g.drawString(num, x - numWidth/2, y + numHeight/2 - 2);
        int childY = y + 40;
        if(node.left != null){
            int childX = x - xOffset;
            drawEdge(g, x, y, childX, childY);
            drawTree(g, node.left, childX, childY, xOffset/2);
        } else {
            int nilX = x - xOffset;
            drawEdge(g, x, y, nilX, childY);
            drawNil(g, nilX, childY);
        }
        if(node.right != null){
            int childX = x + xOffset;
            drawEdge(g, x, y, childX, childY);
            drawTree(g, node.right, childX, childY, xOffset/2);
        } else {
            int nilX = x + xOffset;
            drawEdge(g, x, y, nilX, childY);
            drawNil(g, nilX, childY);
        }
    }

    private void drawEdge(Graphics g, int parentX, int parentY, int childX, int childY) {
        double dx = childX - parentX;
        double dy = childY - parentY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int startX = (int)Math.round(parentX + (dx/dist * nodeRadius));
        int startY = (int)Math.round(parentY + (dy/dist * nodeRadius));
        int endX = (int)Math.round(childX - (dx/dist * nodeRadius));
        int endY = (int)Math.round(childY - (dy/dist * nodeRadius));
        g.setColor(Color.WHITE);
        g.drawLine(startX, startY, endX, endY);
    }

    private void drawNil(Graphics g, int x, int y) {
        g.setColor(Color.GRAY);
        g.fillOval(x - nodeRadius, y - nodeRadius, nodeRadius*2, nodeRadius*2);
        g.setColor(Color.WHITE);
        String txt = "NIL";
        int w = g.getFontMetrics().stringWidth(txt);
        int h = g.getFontMetrics().getAscent();
        g.drawString(txt, x - w/2, y + h/2 - 2);
    }
}

// ----------------------
// Hauptklasse: Main
// ----------------------
public class Main {
    public static String LANGUAGE = "DE";
    public static String currentInputString = "";
    public static void main(String[] args) {
        Object[] options = {"Deutsch", "English"};
        int langChoice = JOptionPane.showOptionDialog(null, "Sprache / Language",
                "Language Selection", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        LANGUAGE = (langChoice == 1) ? "EN" : "DE";
        JFrame frame = new JFrame((LANGUAGE.equals("EN") ? "Red-Black Tree Visualization" : "Rot-Schwarz-Baum Visualisierung") + " (Step by Step)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(new Color(40,40,40));
        TreeVisualizer visualizer = new TreeVisualizer(null);
        frame.add(visualizer);
        frame.setSize(800,600);
        frame.setLocationRelativeTo(null);
        final RedBlackTree[] treeHolder = new RedBlackTree[1];
        treeHolder[0] = new RedBlackTree(visualizer);
        visualizer.setTree(treeHolder[0]);
        currentInputString = JOptionPane.showInputDialog(frame,
                (LANGUAGE.equals("EN") ? "Enter numbers for the tree (comma separated):" :
                        "Gib Zahlen zum Einfügen in den Rot-Schwarz-Baum ein (kommagetrennt):"),
                (LANGUAGE.equals("EN") ? "Input" : "Eingabe"), JOptionPane.QUESTION_MESSAGE);
        if (currentInputString != null && !currentInputString.trim().isEmpty()){
            String[] tokens = currentInputString.trim().split("\\s*,\\s*");
            for(String token : tokens){
                try {
                    treeHolder[0].insert(Integer.parseInt(token));
                } catch(NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, (LANGUAGE.equals("EN") ? "Invalid entry: " : "Ungültiger Eintrag: ") + token,
                            (LANGUAGE.equals("EN") ? "Error" : "Fehler"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        frame.addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_RIGHT) {
                    treeHolder[0].changeStep(1);
                } else if (key == KeyEvent.VK_LEFT) {
                    treeHolder[0].changeStep(-1);
                } else if (key == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                } else if (key == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        String newInput = (String)JOptionPane.showInputDialog(frame,
                                Localization.get("modifyInputMessage"),
                                Localization.get("modifyInputTitle"),
                                JOptionPane.QUESTION_MESSAGE, null, null, currentInputString);
                        if (newInput != null && !newInput.trim().isEmpty()){
                            currentInputString = newInput.trim();
                            RedBlackTree newTree = new RedBlackTree(visualizer);
                            treeHolder[0] = newTree;
                            visualizer.setTree(newTree);
                            String[] tokens = currentInputString.split("\\s*,\\s*");
                            for(String token : tokens){
                                try {
                                    newTree.insert(Integer.parseInt(token));
                                } catch(NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(frame, (LANGUAGE.equals("EN") ? "Invalid entry: " : "Ungültiger Eintrag: ") + token,
                                            (LANGUAGE.equals("EN") ? "Error" : "Fehler"), JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    } else {
                        int confirm = JOptionPane.showConfirmDialog(frame,
                                Localization.get("confirmNewMessage"),
                                Localization.get("confirmNewTitle"),
                                JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            RedBlackTree newTree = new RedBlackTree(visualizer);
                            treeHolder[0] = newTree;
                            visualizer.setTree(newTree);
                            currentInputString = JOptionPane.showInputDialog(frame,
                                    (LANGUAGE.equals("EN") ? "Enter numbers for the new tree (comma separated):" :
                                            "Gib Zahlen zum Einfügen in den neuen Baum ein (kommagetrennt):"),
                                    (LANGUAGE.equals("EN") ? "Input" : "Eingabe"),
                                    JOptionPane.QUESTION_MESSAGE);
                            if (currentInputString != null && !currentInputString.trim().isEmpty()){
                                String[] tokens = currentInputString.trim().split("\\s*,\\s*");
                                for(String token : tokens){
                                    try {
                                        newTree.insert(Integer.parseInt(token));
                                    } catch(NumberFormatException ex) {
                                        JOptionPane.showMessageDialog(frame, (LANGUAGE.equals("EN") ? "Invalid entry: " : "Ungültiger Eintrag: ") + token,
                                                (LANGUAGE.equals("EN") ? "Error" : "Fehler"), JOptionPane.ERROR_MESSAGE);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        frame.setVisible(true);
    }
}