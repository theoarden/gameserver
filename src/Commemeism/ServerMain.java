package Commemeism;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;
import simulation.Ball;
import simulation.Box;

public class ServerMain extends Thread{
    
    private final int width, height;
    private final ArrayList<Box> walls = new ArrayList<>();
    private final ArrayList<Box> voters = new ArrayList<>();
    private final HashMap<Client, Box> clients = new HashMap<>();
    private final ArrayList<Ball> balls = new ArrayList<>();
    private final WeakHashMap<Ball, Client> ballOwner = new WeakHashMap<>();
    private final Score[] scores = new Score[]{
        new Score(0), new Score(1)
    };
    private final ServerSocket server; 

    public static void main(String[] args) throws Exception{
        new ServerMain().start();
    }
    
    private void removeClient(Client c){
        synchronized(clients){
            clients.remove(c);
        }
        announceRemove(c);
    }
    
    private ServerMain() throws Exception{
        width = 800;
        height = 600;
        walls.add(new Box(0, height - 315, 300, 15, false));
        server = new ServerSocket(8000);
        new Thread(() -> {
            try{
                while(true){
                    Socket s = server.accept();
                    new Thread(() -> {
                        try{
                            Client c = new Client(s);
                            synchronized(clients){
                                clients.put(c, c.box);
                            }
                            c.initialize();
                            c.start();
                            announceChange(c);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }).start();
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }).start();
    }
    
    @Override
    public void run(){
        try{
            while(true){
                synchronized(balls){
                    for(Iterator<Ball> i = balls.iterator(); i.hasNext(); ){
                        Ball b = i.next();
                        b.move(1);
                        int x = (int) b.getRay().origin.x;
                        int y = (int) b.getRay().origin.y;
                        if(x < 0 || y < 0 || x > width || y > height || 
                                isOccupied(x, y, x, y, null, walls) != null){
                            i.remove();
                            announceRemove(b);
                        }else{
                            Box voter = isOccupied(x, y, x, y, null, voters);
                            if(voter != null){
                                System.out.println("Score from " + ballOwner.get(b).side);
                                scores[ballOwner.get(b).side].score++;
                                scores[1-ballOwner.get(b).side].score--;
                                i.remove();
                                announceRemove(b);
                                announceChange(scores[0]);
                                announceChange(scores[1]);
                            }
                        }
                    }
                }
                Thread.sleep(20);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void announceMove(Client who){
        synchronized(clients){
            for(Client c : clients.keySet()){
                if(c != who)
                    c.changed(who, true);
            }
        }
    }
    
    private void announceRemove(Object o){
        synchronized(clients){
            for(Client c : clients.keySet()){
                c.changed(o, false);
            }
        }
    }
    
    private void announceChange(Object o){
        synchronized(clients){
            for(Client c : clients.keySet()){
                c.changed(o, true);
            }
        }
    }
    
    private Box isOccupied(int x1, int y1, int x2, int y2, Box except, Collection<Box>... boxes){
        for(Collection<Box> rr : boxes)
            for(Box r : rr){
                if(r == except)
                    continue;
                if(x1 < r.x + r.width && x2 > r.x && y1 < r.y + r.height && y2 > r.y)
                    return r;
            }
        return null;
    }
    
    private void addPropaganda(Client from, Ball obj){
        synchronized(balls){
            balls.add(obj);
            ballOwner.put(obj, from);
        }
    }
    
    private class Client extends Thread{
        public final Box box;
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final String name;
        private final int side;
        private final HashMap<Object, Boolean> changes = new HashMap<>();
        // 2nd parameter: change if true, removal if false
        private long lastShoot = 0;
        
        public Client(Socket s) throws Exception{
            socket = s;
            out = new DataOutputStream(s.getOutputStream());
            in = new DataInputStream(s.getInputStream());
            name = in.readUTF();
            side = in.readInt();
            if(side == 0){
                box = new Box(100, height-100, 75, 75, true);
            }else{
                box = new Box(width - 100, 100, 75, 75, true);
            }
        }
        
        public void initialize() throws IOException{
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(scores[0].score);
            out.writeInt(scores[1].score);
            
            out.writeInt(box.x);
            out.writeInt(box.y);
            out.writeInt(box.width);
            out.writeInt(box.height);
            
            out.writeInt(walls.size());
            for(Box w : walls){
                out.writeInt(w.x);
                out.writeInt(w.y);
                out.writeInt(w.width);
                out.writeInt(w.height);
            }
            
            out.writeInt(voters.size());
            for(Box w : voters){
                out.writeInt(w.x);
                out.writeInt(w.y);
                out.writeInt(w.width);
                out.writeInt(w.height);
            }
            
            ArrayList<Client> arr;
            synchronized(clients){
                arr = new ArrayList<>(clients.keySet());
            }
            out.writeInt(arr.size());
            for(Client c : arr){
                out.writeInt(c.box.id);
                out.writeInt(c.side);
                out.writeUTF(c.name);
                out.writeInt(c.box.x);
                out.writeInt(c.box.y);
                out.writeInt(c.box.width);
                out.writeInt(c.box.height);
            }
            
            out.flush();

            new Thread(() -> {
                try{
                    while(true){
                        HashMap<Object, Boolean> chg = new HashMap<>();
                        synchronized(changes){
                            changes.wait();
                            chg.putAll(changes);
                            changes.clear();
                        }
                        for(Object o : chg.keySet()){
                            out.writeBoolean(chg.get(o));
                            if(o instanceof Ball){
                                Ball b = (Ball) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_BALL);
                                out.writeInt(b.id);
                                out.writeInt((int)b.getRay().origin.x);
                                out.writeInt((int)b.getRay().origin.y);
                                out.writeInt((int)(b.getRay().v.dX * b.getRay().speed));
                                out.writeInt((int)(b.getRay().v.dY * b.getRay().speed));
                            }else if(o instanceof Client){
                                Client c = (Client) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_CLIENT);
                                out.writeInt(c.box.id);
                                out.writeInt(c.side);
                                out.writeUTF(c.name);
                                out.writeInt(c.box.x);
                                out.writeInt(c.box.y);
                                out.writeInt(c.box.width);
                                out.writeInt(c.box.height);
                            }else if(o instanceof Score){
                                Score sc = (Score) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_SCORE);
                                out.writeInt(sc.team);
                                out.writeInt(sc.score);
                            }
                        }
                        out.flush();
                        chg.clear();
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
                try{ socket.close(); }catch(Exception e){}
            }).start();
        }
        
        public void changed(Object another, boolean isedited){
            synchronized(changes){
                changes.put(another, isedited);
            }
        }
        
        private void move(int direction){
            int dx = 0, dy = 0;
            int distance = 5;
            switch(direction){
                case 0: dy = 1; break;
                case 1: dx = 1; break;
                case 2: dy = -1; break;
                case 3: dx = -1; break;
            }
            while(distance > 0 && box.x + dx * distance < 0) distance--;
            while(distance > 0 && box.y + dy * distance < 0) distance--;
            while(distance > 0 && box.x + box.width + dx * distance > width) distance--;
            while(distance > 0 && box.y + box.height + dy * distance > height) distance--;
            while(distance > 0 && null != isOccupied(
                    box.x + dx * distance, box.y + dy * distance, box.x + dx * distance + box.width, box.y + dy * distance + box.height, 
                    this.box, walls, clients.values())) distance--;
            if(distance == 0) return;
            box.move(dx * distance, dy * distance);
            announceMove(this);
        }
        
        private void throwPropaganda(int direction){
            if(lastShoot + 200 < System.currentTimeMillis())
                return;
            lastShoot = System.currentTimeMillis();
            int dx = 0, dy = 0;
            int x = box.x, y = box.y;
            int distance = 3;
            switch(direction){
                case 0: dy = 1; x += box.width / 2; y += box.height; break;
                case 1: dx = 1; x += box.width; y += box.height / 2; break;
                case 2: dy = -1; x += box.width / 2; break;
                case 3: dx = -1; y += box.height / 2; break;
            }
            addPropaganda(this, new Ball(x, y, dx * distance, dy * distance));
        }
        
        @Override
        public void run(){
            try{
                while(true){
                    switch(in.readInt()){
                        case MessageCodes.CLIENT_MOVE:{
                            move(in.readInt());
                            break;
                        }
                        case MessageCodes.CLIENT_THROW:{
                            throwPropaganda(in.readInt());
                            break;
                        }
                    }
                }
            }catch(IOException e){
                e.printStackTrace();
            }
            try{ socket.close(); }catch(Exception e){}
            removeClient(this);
        }
    }
    
    private class Score{
        public final int team;
        public int score;
        
        public Score(int team){
            this.team = team;
        }
    }
}