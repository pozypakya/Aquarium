package com.kfriede.Aquarium;

import Neptune.handlers.CommandFileHandler;
import Neptune.handlers.InputFileHandler;
import Neptune.handlers.MessageHandler;
import Neptune.models.Command;
import Neptune.models.Parameter;
import Neptune.models.Television;
import com.kfriede.Aquarium.handlers.StorageHandler;
import com.kfriede.Aquarium.util.AboutWindow;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalTime;
import java.util.List;

public class MainWindow {
	private final String COMMAND_LIST_FILE = "commands.json";
	private static Logger log = Logger.getLogger(MainWindow.class.getName());
	
	@FXML private AnchorPane mainAnchor;
	
	@FXML private Menu fileMenu;
	@FXML private Menu editMenu;
	@FXML private Menu helpMenu;
	
	@FXML private CheckMenuItem selectAll;
	
	@FXML private Label threadCountLabel;
	
	@FXML private ComboBox<Television> nodeComboBox;
	@FXML private ComboBox<Command> commandComboBox;
	@FXML private ComboBox<Parameter> parameterComboBox;
	
	@FXML private Button helpButton;
	@FXML private Button sendButton;
	
	@FXML private TextArea consoleTextArea;
	
	private List<Television> tvList;
	private List<Command> commandList;
	
	private Television selectedNode;
	private Command selectedCommand;
	private Parameter selectedParameter;
	
	private final ThreadGroup threadManager = new ThreadGroup("Active Sockets");
	
	
	@FXML
	private void initialize() {
		
		StorageHandler.initialize();
		log.debug("Application started");
		log.debug(System.getProperty("aquarium.root"));

		buildMenu();
		setConsoleTextAreaListener();
		
		setListeners();
		loadDefaults();
		
	}
	
	private void buildMenu() {
		
		/**
		 * File Menu
		 */
		MenuItem openFile = new MenuItem("Open File");
        openFile.setOnAction(t -> handleFileOpen());
        
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(t -> Platform.exit());
        
        fileMenu.getItems().addAll(openFile, exit);
        
        
        
        /**
         * Edit Menu
         */
        selectAll = new CheckMenuItem("Select All");
        selectAll.setOnAction(t -> handleSelectAll());
        
        MenuItem clear = new MenuItem("Clear");
        clear.setOnAction(t -> {
            nodeComboBox.getSelectionModel().clearSelection();
            commandComboBox.getSelectionModel().clearSelection();
            parameterComboBox.getSelectionModel().clearSelection();

            consoleTextArea.setText("");

            selectAll.setSelected(false);
            handleSelectAll();
        });
        
        editMenu.getItems().addAll(selectAll, new SeparatorMenuItem(), clear);
        
        
        /**
         * Help Menu
         */
        MenuItem about = new MenuItem("About");
        about.setOnAction(t -> AboutWindow.buildWindow().showAndWait());
        
        helpMenu.getItems().addAll(about);
	}
	
	
	/**
	 * Loads nodes from a nodeList into combobox
	 * @param tvList
	 */
	private void loadNodes(List<Television> tvList) {
		this.tvList = tvList;
		
		nodeComboBox.getItems().clear();
		
		for (Television tv : this.tvList) {
			nodeComboBox.getItems().add(tv);
		}
	}
	
	/**
	 * Loads commands into combobox from a commandlist
	 * @param commandList
	 */
	private void loadCommands(List<Command> commandList) {
		this.commandList = commandList;
		
		commandComboBox.getItems().clear();
		
		for (Command cmd : this.commandList) {
			commandComboBox.getItems().add(cmd);
		}
	}
	
	/**
	 * Loads parameters given a selected command
	 * @param command
	 */
	private void loadParameters(Command command) {
		
		parameterComboBox.getItems().clear();
		
		for (Parameter parameter : command.getParameters()) {
			parameterComboBox.getItems().add(parameter);
		}
	}
	
	/**
	 * Handles when user selects to open a new file
	 */
	private void handleFileOpen() {
		FileChooser fileChooser = new FileChooser();
    	fileChooser.setTitle("Open Resource File");
    	File inFile = fileChooser.showOpenDialog(mainAnchor.getScene().getWindow());
    	
    	if (inFile != null) {   		
    		try {
				loadNodes(InputFileHandler.parseFile(inFile.getAbsolutePath()));
				append("Opened " + inFile.getAbsolutePath() + " successfully");

				// save reference to file location in properties
				StorageHandler.PROPERTIES.setProperty("last_used_nodes_file", inFile.getAbsolutePath());

			} catch (FileNotFoundException | JSONException e) {
				append("Error opening file: " + e.getMessage().toString());
				return;
			}

    	}
    	
	}
	
	/**
	 * Handler<br>
	 * Handles selection of Node combo box
	 * @param newSelection
	 */
	private void handleNodeSelection(Television newSelection) {
		selectedNode = newSelection;
	}
	
	/**
	 * Handler<br>
	 * Handles selection of Command combo box
	 * @param newSelection
	 */
	private void handleCommandSelection(Command newSelection) {
		selectedCommand = newSelection;
		
		if (selectedCommand != null) {
			loadParameters(newSelection);
		}
	}
	
	/**
	 * Handler<br>
	 * Handles selection of the Parameter combo box
	 * @param newSelection
	 */
	private void handleParameterSelection(Parameter newSelection) {
		selectedParameter = newSelection;
	}
	
	/**
	 * Handler<br>
	 * Sends selected values to selected TV(s)
	 */
	private void handleSendButtonClick() {
		clearConsole();
		final int timeout;

		int parser = 200;
		try {
			parser = Integer.parseInt(StorageHandler.PROPERTIES.getProperty("connection_timeout"));
		} catch (NumberFormatException e) {
			log.debug("Error parsing connection_timeout value from properties: " + e.getMessage() + "  Using default value of 200ms");
		}
		timeout = parser;

		if (threadManager.activeCount() > 0) {
			threadManager.destroy();
			updateThreadCountLabel();
			return;
		}
		
		if (selectedCommand == null) {
			append("Abort: Please select a command");
		} else if (selectedParameter == null) {
			append("Abort: Please select a parameter");
		} else {
			Thread runner;
			
			if (selectAll.isSelected()) {
				for (final Television t : tvList) {
					runner = new Thread(threadManager, "") {
						public void run() {
							updateThreadCountLabel();

							String returnedStatus = MessageHandler.sendCommand(t.getIp(), t.getPort(), timeout, t.getUsername(), t.getPassword(), selectedCommand.getCommand(), selectedParameter.getValue());
							if (!returnedStatus.equalsIgnoreCase("OK")) {
								append("Error: " + t.getName() + " <" + t.getIp() + ">: " + returnedStatus);
							}

				        	updateThreadCountLabel();
						}
					};
					
					runner.setDaemon(true);
					runner.start();
				}
			} else {
				if (selectedNode == null) {
					append("Abort: Please select a node");
				} else {
					runner = new Thread(threadManager, "") {
					    public void run() {
					    	updateThreadCountLabel();
				        	append(selectedNode.getName() + " <" + selectedNode.getIp() + ">: " + MessageHandler.sendCommand(selectedNode.getIp(), selectedNode.getPort(), timeout, selectedNode.getUsername(), selectedNode.getPassword(), selectedCommand.getCommand(), selectedParameter.getValue()));
				        	updateThreadCountLabel();
					    }
					};
					
					runner.setDaemon(true);
					runner.start();
				}
			}
		}
	}
	
	/**
	 * Handler<br>
	 * Selects/Deselects all nodes available based on user action
	 */
	private void handleSelectAll() {
		nodeComboBox.setDisable((selectAll.isSelected()));
	}
	
	/**
	 * Handler<br>
	 * Updates the thread counter label with the current number of active threads
	 */
	private void updateThreadCountLabel() {
		Platform.runLater(() -> threadCountLabel.setText("Threads: " + threadManager.activeCount()));
	}
	
	/**
	 * Loads commands and nodes from default locations
	 */
	private void loadDefaults() {
		/**
		 * Load command list
		 */
		try {
			loadCommands(CommandFileHandler.parseFile(this.getClass().getClassLoader().getResourceAsStream(COMMAND_LIST_FILE)));
		} catch (FileNotFoundException e) {
			append(e.getMessage().toString());
		}
		
		
		/**
		 * Load Televisions from last opened file
		 */
		try {
			String nodes_file = StorageHandler.PROPERTIES.getProperty("last_used_nodes_file");
			
			loadNodes(InputFileHandler.parseFile(nodes_file));
			append("Loaded nodes from " + nodes_file);
			
		} catch (FileNotFoundException e) {
			append("Error opening last used nodes file: " + e.getMessage().toString());
		}
	}
	
	/**
	 * Builds listeners and actions for ComboBoxes 
	 */
	private void setListeners() {
		/**
		 * Set comboBox listeners
		 */
		nodeComboBox.valueProperty().addListener(new ChangeListener<Television>() {
	        public void changed(ObservableValue<? extends Television> ov, Television t, Television t1) {
	        	handleNodeSelection(t1);
	        }
	    });
		commandComboBox.valueProperty().addListener(new ChangeListener<Command>() {
	        public void changed(ObservableValue<? extends Command> ov, Command t, Command t1) {
	        	handleCommandSelection(t1);
	        }    
	    });
		parameterComboBox.valueProperty().addListener(new ChangeListener<Parameter>() {
	        public void changed(ObservableValue<? extends Parameter> ov, Parameter t, Parameter t1) {
	        	handleParameterSelection(t1);
	        }    
	    });
		sendButton.setOnAction(new EventHandler<ActionEvent>() {
		    public void handle(ActionEvent e) {
		        handleSendButtonClick();
		    }
		});
	}
	
	/**
	 * Convenience method for appending to consoleTextArea
	 * @param text Text to Append
	 */
	private void append(final String text) {
		Platform.runLater(new Runnable() {
			public void run() {
				consoleTextArea.appendText(LocalTime.now() + ": " + text + "\n");
			}
		});

		log.info(text);
	}
	
	/**
	 * Handler<br>
	 * Creates behavior of consoleTextArea auto scrolling with appended text
	 */
	private void setConsoleTextAreaListener() {
		consoleTextArea.textProperty().addListener(new ChangeListener<Object>() {
		    public void changed(ObservableValue<?> observable, Object oldValue,
		            Object newValue) {
		        consoleTextArea.setScrollTop(Double.MIN_VALUE);
		    }
		});
	}

	/**
	 * Clears console in preparation for new command(s) to be sent
	 */
	private void clearConsole() {
		consoleTextArea.setText("");
	}
}
