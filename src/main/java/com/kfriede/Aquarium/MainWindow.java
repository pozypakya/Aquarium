package com.kfriede.Aquarium;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.json.JSONException;

import com.kfriede.Aquarium.models.Command;
import com.kfriede.Aquarium.models.Parameter;
import com.kfriede.Aquarium.models.Television;
import com.kfriede.Aquarium.util.AboutWindow;
import com.kfriede.Aquarium.util.CommandFileHandler;
import com.kfriede.Aquarium.util.InputFileHandler;
import com.kfriede.Aquarium.util.MessageHandler;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

public class MainWindow {
	private final String COMMAND_LIST_FILE = "commands.json";
	
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
	
	private ThreadGroup threadManager = new ThreadGroup("Active Sockets");
	
//	private PopOver popOver = new PopOver();
//	private Label popOverLabel = new Label();
	
	@FXML
	private void initialize() {
		
		try {
			
			buildMenu();
			setConsoleTextAreaListener();
			
//			popOver.setArrowLocation(PopOver.ArrowLocation.TOP_RIGHT);
//			popOver.setContentNode(popOverLabel);
			
			setListeners();
			loadDefaults();
			
		} catch (Exception ex) {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error");
			alert.setHeaderText("Unhandled Exception:");
			alert.setContentText(ex.getMessage().toString());

			// Create expandable Exception.
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			String exceptionText = sw.toString();

			Label label = new Label("The exception stacktrace was:");

			TextArea textArea = new TextArea(exceptionText);
			textArea.setEditable(false);
			textArea.setWrapText(true);

			textArea.setMaxWidth(Double.MAX_VALUE);
			textArea.setMaxHeight(Double.MAX_VALUE);
			GridPane.setVgrow(textArea, Priority.ALWAYS);
			GridPane.setHgrow(textArea, Priority.ALWAYS);

			GridPane expContent = new GridPane();
			expContent.setMaxWidth(Double.MAX_VALUE);
			expContent.add(label, 0, 0);
			expContent.add(textArea, 0, 1);

			// Set expandable Exception into the dialog pane.
			alert.getDialogPane().setExpandableContent(expContent);

			alert.showAndWait();
		}
		
	}
	
	private void buildMenu() {
		
		/**
		 * File Menu
		 */
		MenuItem openFile = new MenuItem("Open File");
        openFile.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
            	handleFileOpen();
            }
        });  
        
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
            	Platform.exit();
            }
        });  
        
        fileMenu.getItems().addAll(openFile, exit);
        
        /**
         * Edit Menu
         */
        
        selectAll = new CheckMenuItem("Select All");
        selectAll.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
            	handleSelectAll();
            }
        });  
        
        MenuItem clear = new MenuItem("Clear");
        clear.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
            	nodeComboBox.getSelectionModel().clearSelection();
            	commandComboBox.getSelectionModel().clearSelection();
            	parameterComboBox.getSelectionModel().clearSelection();
            	
            	consoleTextArea.setText("");
            	
            	selectAll.setSelected(false);
            	handleSelectAll();
            }
        });  
        
        editMenu.getItems().addAll(selectAll, new SeparatorMenuItem(), clear);
        
        
        /**
         * Help Menu
         */
        
        MenuItem about = new MenuItem("About");
        about.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
            	AboutWindow.buildWindow().showAndWait();
            }
        });  
        
        helpMenu.getItems().addAll(about);
	}
	
	
	private void loadNodes(List<Television> tvList) {
		this.tvList = tvList;
		
		nodeComboBox.getItems().clear();
		
		for (Television tv : this.tvList) {
			nodeComboBox.getItems().add(tv);
		}
	}
	
	private void loadCommands(List<Command> commandList) {
		this.commandList = commandList;
		
		commandComboBox.getItems().clear();
		
		for (Command cmd : this.commandList) {
			commandComboBox.getItems().add(cmd);
		}
	}
	
	private void loadParameters(Command command) {
		
		parameterComboBox.getItems().clear();
		
		for (Parameter parameter : command.getParameters()) {
			parameterComboBox.getItems().add(parameter);
		}
	}
	
	private void handleFileOpen() {
		FileChooser fileChooser = new FileChooser();
    	fileChooser.setTitle("Open Resource File");
    	File inFile = fileChooser.showOpenDialog(mainAnchor.getScene().getWindow());
    	
    	if (inFile != null) {   		
    		try {
				loadNodes(InputFileHandler.parseFile(inFile.getAbsolutePath()));
				append("Opened " + inFile.getAbsolutePath() + " successfully");
			} catch (FileNotFoundException | JSONException e) {
				append("Error opening file: " + e.getMessage().toString());
				return;
			}
    		
    		MainApp.PROPERTIES.setProperty("lastUsedFile", inFile.getAbsolutePath());
    	}
    	
	}
	
	private void handleNodeSelection(Television newSelection) {
		selectedNode = newSelection;
	}
	
	private void handleCommandSelection(Command newSelection) {
		selectedCommand = newSelection;
		
		if (selectedCommand != null) {
			loadParameters(newSelection);
		}
	}
	
	private void handleParameterSelection(Parameter newSelection) {
		selectedParameter = newSelection;
	}
	
	private void handleSendButtonClick() {
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
			Thread runner = null;
			
			if (selectAll.isSelected()) {
				for (final Television t : tvList) {
					runner = new Thread(threadManager, "") {
						public void run() {
							updateThreadCountLabel();
				        	append(t.getName() + " <" + t.getIp() + ">: " + MessageHandler.sendCommand(t.getIp(), t.getPort(), t.getUsername(), t.getPassword(), selectedCommand.getCommand(), selectedParameter.getValue()));
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
				        	append(selectedNode.getName() + " <" + selectedNode.getIp() + ">: " + MessageHandler.sendCommand(selectedNode.getIp(), selectedNode.getPort(), selectedNode.getUsername(), selectedNode.getPassword(), selectedCommand.getCommand(), selectedParameter.getValue()));
				        	updateThreadCountLabel();
					    }  
					};
					
					runner.setDaemon(true);
					runner.start();
				}
			}
		}
	}
	
	private void handleSelectAll() {
		nodeComboBox.setDisable((selectAll.isSelected() ? true : false));
	}
	
	private void updateThreadCountLabel() {
		Platform.runLater(new Runnable() {
			public void run() {
				threadCountLabel.setText("Threads: " + threadManager.activeCount());
			}
		});
	}
	
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
			loadNodes(InputFileHandler.parseFile(MainApp.PROPERTIES.getProperty("nodes_file")));
			append("Loaded nodes from " + MainApp.PROPERTIES.getProperty("nodes_file"));
		} catch (FileNotFoundException e) {
			append("Error opening default nodes file: " + e.getMessage().toString());
		}
	}
	
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
//		helpButton.setOnAction(new EventHandler<ActionEvent>() {
//		    @Override public void handle(ActionEvent e) {
//		        handleHelpButtonClick();
//		    }
//		});
		sendButton.setOnAction(new EventHandler<ActionEvent>() {
		    public void handle(ActionEvent e) {
		        handleSendButtonClick();
		    }
		});
	}
	
	private void append(final String text) {
		Platform.runLater(new Runnable() {
			public void run() {
				consoleTextArea.appendText(text + "\n");
			}
		});
	}
	
	private void setConsoleTextAreaListener() {
		consoleTextArea.textProperty().addListener(new ChangeListener<Object>() {
		    public void changed(ObservableValue<?> observable, Object oldValue,
		            Object newValue) {
		        consoleTextArea.setScrollTop(Double.MIN_VALUE); //this will scroll to the bottom
		        //use Double.MIN_VALUE to scroll to the top
		    }
		});
	}
}