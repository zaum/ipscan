package net.azib.ipscan.gui.fx.feeders;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.FeederException;
import net.azib.ipscan.feeders.RangeFeeder;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.List;

import static net.azib.ipscan.config.Labels.getLabel;
import static net.azib.ipscan.util.InetAddressUtils.*;
import net.azib.ipscan.util.InetAddressUtils;

/**
 * JavaFX GUI for RangeFeeder.
 * Replaces the SWT-based RangeFeederGUI.java.
 */
public class FXRangeFeederGUI extends FXAbstractFeederGUI {

    private TextField startIPField;
    private TextField endIPField;
    private TextField hostnameField;
    private ComboBox<String> netmaskCombo;
    private RangeFeeder rangeFeeder;

    private boolean isEndIPUnedited = true;
    private boolean modifyListenersDisabled = false;

    public FXRangeFeederGUI() {
        rangeFeeder = new RangeFeeder();
        feeder = rangeFeeder;
        initialize();
    }

    @Override
    public void initialize() {
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        // Row 0: IP range
        var ipRangeLabel = new Label(getLabel("feeder.range") + ":");
        ipRangeLabel.getStyleClass().add("controls-label");

        startIPField = new TextField();
        startIPField.getStyleClass().add("controls-field");
        startIPField.setPrefColumnCount(15);
        startIPField.setText("192.168.0.1");

        var toLabel = new Label(getLabel("feeder.range.to"));
        toLabel.getStyleClass().add("controls-label");

        endIPField = new TextField();
        endIPField.getStyleClass().add("controls-field");
        endIPField.setPrefColumnCount(15);
        endIPField.setText("192.168.0.254");
        netmaskCombo = new ComboBox<>();
        netmaskCombo.getStyleClass().add("controls-combo");
        netmaskCombo.getItems().addAll(
            getLabel("feeder.range.netmask"), "/26", "/24", "/16",
            "255...192", "255...128", "255...0", "255..0.0", "255.0.0.0"
        );
        netmaskCombo.setValue(getLabel("feeder.range.netmask"));

        // Hostname
        var hostnameLabel = new Label(getLabel("feeder.range.hostname") + ":");
        hostnameLabel.getStyleClass().add("controls-label");

        hostnameField = new TextField();
        hostnameField.getStyleClass().add("controls-field");
        hostnameField.setPrefColumnCount(15);

        var ipUpButton = new Button(getLabel("button.ipUp"));
        ipUpButton.getStyleClass().add("dialog-button");
        ipUpButton.setOnAction(e -> {
            try {
                var ifaces = getNetworkInterfaces();
                var menu = new ContextMenu();
                for (var iface : ifaces) {
                    // sort IPv4 (4-byte) before IPv6 (16-byte), matching SWT FeederActions
                    var addresses = iface.getInterfaceAddresses().stream()
                        .filter(a -> a != null && !a.getAddress().isLoopbackAddress())
                        .sorted(java.util.Comparator.comparingInt(a -> a.getAddress().getAddress().length))
                        .toList();
                    for (var ifaddr : addresses) {
                        var ip = ifaddr.getAddress().getHostAddress();
                        final String fip = ip;
                        final int fprefix = ifaddr.getNetworkPrefixLength();
                        var item = new MenuItem(iface.getDisplayName() + ": " + ip + "/" + fprefix);
                        item.setOnAction(ev -> {
                            startIPField.setText(fip);
                            try {
                                var mask = parseNetmask(fprefix).getHostAddress();
                                var netmaskItems = netmaskCombo.getItems();
                                for (var i = 0; i < netmaskItems.size(); i++) {
                                    if (netmaskItems.get(i).equals(mask) || netmaskItems.get(i).endsWith(mask.replaceFirst("0+$", "0"))) {
                                        netmaskCombo.getSelectionModel().select(i);
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
                        menu.getItems().add(item);
                    }
                }
                if (menu.getItems().isEmpty()) {
                    var noItem = new MenuItem("No interfaces found");
                    noItem.setDisable(true);
                    menu.getItems().add(noItem);
                }
                var pos = ipUpButton.localToScreen(0, ipUpButton.getHeight());
                menu.show(ipUpButton, pos.getX(), pos.getY());
            } catch (Exception ex) {
                // ignore
            }
        });

        grid.add(ipRangeLabel, 0, 0);
        grid.add(startIPField, 1, 0);
        grid.add(toLabel, 2, 0);
        grid.add(endIPField, 3, 0);
        grid.add(netmaskCombo, 4, 0);
        grid.add(hostnameLabel, 5, 0);
        grid.add(hostnameField, 6, 0);
        grid.add(ipUpButton, 7, 0);

        getChildren().add(grid);

        // Listeners
        startIPField.textProperty().addListener((obs, old, val) -> {
            if (isEndIPUnedited) endIPField.setText(val);
        });

        endIPField.textProperty().addListener((obs, old, val) -> {
            isEndIPUnedited = false;
        });

        netmaskCombo.setOnAction(e -> handleNetmask());

        asyncFillLocalHostInfo();
    }

    private void asyncFillLocalHostInfo() {
        new Thread(() -> {
            synchronized (FXRangeFeederGUI.class) {
                var localInterface = InetAddressUtils.getLocalInterface();
                final var localInterfaceRef = localInterface;
                String localName;
                try {
                    localName = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
                    localName = localInterfaceRef != null ? localInterfaceRef.getAddress().getHostName() : "localhost";
                }
                final var localNameRef = localName;
                javafx.application.Platform.runLater(() -> {
                    if (hostnameField.getText().isEmpty()) {
                        hostnameField.setText(localNameRef);
                    }
                    if (startIPField.getText().isEmpty() && localInterfaceRef != null) {
                        startIPField.setText(localInterfaceRef.getAddress().getHostAddress());
                        try {
                            var mask = parseNetmask(localInterfaceRef.getNetworkPrefixLength()).getHostAddress();
                            selectNetmask(mask);
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void selectNetmask(String mask) {
        var netmaskItems = netmaskCombo.getItems();
        for (var i = 0; i < netmaskItems.size(); i++) {
            var item = netmaskItems.get(i);
            if (item.equals(mask) || (item.contains("...") && item.replace("...", ".0.").equals(mask))) {
                netmaskCombo.getSelectionModel().select(i);
                return;
            }
        }
        // not in the list, select the custom option
        var prefix = mask.startsWith("255.") ? toPrefix(mask) : -1;
        if (prefix > 0) {
            for (var i = 0; i < netmaskItems.size(); i++) {
                if (netmaskItems.get(i).equals("/" + prefix)) {
                    netmaskCombo.getSelectionModel().select(i);
                    return;
                }
            }
        }
    }

    private int toPrefix(String mask) {
        int bits = 0;
        for (var part : mask.split("\\.")) {
            var value = Integer.parseInt(part);
            while (value > 0) {
                bits += value & 1;
                value >>= 1;
            }
        }
        return bits;
    }

    @Override
    public Feeder createFeeder() {
        feeder = rangeFeeder = new RangeFeeder(
            startIPField.getText().trim(),
            endIPField.getText().trim()
        );
        return feeder;
    }

    @Override
    public String[] serialize() {
        return new String[] {startIPField.getText().trim(), endIPField.getText().trim()};
    }

    @Override
    public void unserialize(String[] parts) {
        startIPField.setText(parts[0]);
        endIPField.setText(parts[1]);
    }

    @Override
    public String[] serializePartsLabels() {
        return new String[] {"feeder.range.startIP", "feeder.range.endIP"};
    }

    private void handleNetmask() {
        var value = netmaskCombo.getValue();
        if (value == null || value.equals(getLabel("feeder.range.netmask"))) return;
        try {
            var startIP = InetAddress.getByName(startIPField.getText());
            var netmask = parseNetmask(value);
            modifyListenersDisabled = true;
            startIPField.setText(startRangeByNetmask(startIP, netmask).getHostAddress());
            endIPField.setText(endRangeByNetmask(startIP, netmask).getHostAddress());
            modifyListenersDisabled = false;
            isEndIPUnedited = false;
        } catch (UnknownHostException e) {
            throw new FeederException("invalidNetmask");
        }
    }
}
