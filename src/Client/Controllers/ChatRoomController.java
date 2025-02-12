package Client.Controllers;

import Client.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class ChatRoomController implements Initializable {
    @FXML private TextArea txtDisplay;
    @FXML private TextField txtInput;
    @FXML private Button backBtn, sendBtn, connBtn;
    @FXML private ListView contactList, waitingList;
    ObservableList<GridPane> items = FXCollections.observableArrayList();

    Socket socket = null;
    int std_id;
    // 임시
    int room_id = 5;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addTextLimiter(txtInput, 256);
        std_id = Integer.parseInt(UserInfo.getId());

        txtInput.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent k) {
                if(k.getCode().equals(KeyCode.ENTER)) {
                    // 채팅 메세지를 전송한다.
                    MessagePacker packet = new MessagePacker(std_id, room_id,txtInput.getText());
                    send(packet.getPacket());
                    txtInput.clear();
                }
            }
        });

        backBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Stage stage = new Stage();
                try {
                    SocketConnection.close();
                    // 현재 창을 종료한다.
                    Stage currStage = (Stage) backBtn.getScene().getWindow();
                    currStage.close();
                    // 새 창을 띄운다.
                    Parent root = (Parent) FXMLLoader.load(getClass().getResource("/Client/Views/Login.fxml"));
                    Scene scene = new Scene(root);
                    stage.setScene(scene);
                    stage.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        sendBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                MessagePacker packet = new MessagePacker(std_id, room_id, txtInput.getText());
                send(packet.getPacket());
                txtInput.clear();
            }
        });

        connBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
//                Protocol packet = new Protocol();
//                send(packet.getPacket());
//            	contactList.setItems(items);
            }
        });

        startChatting();
    }

    public static void addTextLimiter(final TextField tf, final int maxLength) {
        tf.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> ov, final String oldValue, final String newValue) {
                if (tf.getText().length() > maxLength) {
                    String s = tf.getText().substring(0, maxLength);
                    tf.setText(s);
                }
            }
        });
    }

    void startChatting() {
        try {
            // 소켓을 연결한다.
            SocketConnection.connect();
            socket = SocketConnection.socket;

            URL url = new URL("http://localhost:3000/chatMessage?room_id="+room_id);
            HttpURLConnection http = (HttpURLConnection) url.openConnection();
            http.setRequestMethod("GET");
            http.setRequestProperty("Authorization", UserInfo.getId()+":"+UserInfo.getPw());

            if(http.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String resBody = getResponseBody(http.getInputStream());
                System.out.println(resBody);
                JSONParser parser = new JSONParser();
                JSONArray list = (JSONArray)parser.parse(resBody);
                for(int i=0; i<list.size(); i++) {
                    JSONObject obj = (JSONObject) list.get(i);
                    Platform.runLater(()->displayText("["+obj.get("std_id")+"]" + obj.get("message")));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        Thread thread = new Thread() {
            @Override
            public void run() {
                if(socket != null || !socket.isClosed()) {
                    receive();
                } else {
                    Platform.runLater(()->displayText("[채팅 서버 통신 안됨]"));
                }
            }
        };
        thread.start();
    }

    void receive() {
        while (true) {
            try {
                InputStream inputStream = socket.getInputStream();
                MessagePacker packet = MessagePacker.unpack(inputStream);

                String msg = packet.getMessage();
                Platform.runLater(()->displayText("["+packet.getStdId()+"]" + msg));
            } catch (Exception e) {
                Platform.runLater(()->displayText("[서버 통신 안됨]"));
                e.printStackTrace();
                SocketConnection.close();
                break;
            }
        }
    }

    void send(byte[] packet) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(packet);
                    outputStream.flush();
                } catch (Exception e) {
                    SocketConnection.close();
                }
            }
        };
        thread.start();
    }

    void displayText(String text) {
        txtDisplay.appendText(text + "\n");
    }

    GridPane createUserInfoBox(String name,String job, int member) {
        GridPane userInfoBox = new GridPane();
        Label userName = new Label();
        Label userJob = new Label();
        Label userState = new Label();

        userName.setText(name);
        userJob.setText(job);
        if(member == 1) {
            userState.setText("참여");
        } else if(member == 0){
            userState.setText("대기");
        }
        Button ejectBtn = new Button();
        ejectBtn.setText("강퇴");
        userInfoBox.add(userState, 0, 0);
        userInfoBox.add(userName, 1, 0);
        userInfoBox.add(userJob, 0, 1);
        userInfoBox.add(ejectBtn, 0, 2);
        return userInfoBox;
    }

    public static String getResponseBody(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
        String line;
        while((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        br.close();
        return sb.toString();
    }

}
