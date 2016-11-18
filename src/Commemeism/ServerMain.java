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
import java.util.concurrent.ThreadLocalRandom;

import simulation.Propaganda;
import simulation.Box;

public class ServerMain extends Thread {

    private final int width, height;
    private Box getPropaganda;
    private final ArrayList<Box> walls = new ArrayList<>();
    private final ArrayList<Box> proletariats = new ArrayList<>();
    private final HashMap<Propagandist, Box> clients = new HashMap<>();
    private final ArrayList<Propaganda> propagandas = new ArrayList<>();
    private final WeakHashMap<Propaganda, Propagandist> propagandaThrower = new WeakHashMap<>();
    private final Influence[] influences = new Influence[]{
            new Influence(0), new Influence(1)
    };
    private final ServerSocket server;

    public static void main(String[] args) throws Exception {
        new ServerMain().start();
    }

    private void removeClient(Propagandist c) {
        synchronized (clients) {
            clients.remove(c);
        }
        announceRemove(c);
    }

    private ServerMain() throws Exception {
        width = 1400;
        height = 750;
        getPropaganda = new Box(width/2 - 120, height/2- 150, 270, 300);
        walls.add(new Box(0, height - 320, 240, 10));
        walls.add(new Box(320, height - 240, 10, 240));
        walls.add(new Box(width - 240, 320, 240, 10));
        walls.add(new Box(width - 320, 0, 10, 240));
        walls.add(new Box(width/2 - 150, height/2 -150, 300, 10));
        walls.add(new Box(width/2 - 150, height/2 -150, 10, 220));
        walls.add(new Box(width/2 + 150, height/2 - 150,10, 300 ));
        walls.add(new Box(width/2 - 150, height/2 + 150,300, 10 ));
        for (int i = 0; i < 5; i++) {
            Box b = new Box(-100, -100, 75, 75);
            int nx, ny;
            do{
                nx = ThreadLocalRandom.current().nextInt(0, width - 75);
                ny = ThreadLocalRandom.current().nextInt(0, height - 75);
            }while(isOccupied(nx, ny, nx+75, ny+75, b, walls, proletariats) != null);
            b.x = nx;
            b.y = ny;
            proletariats.add(b);
        }
        new Thread(() -> {
            try{while(true){
                for(Box b : proletariats) {
                    int nx, ny;
                    do{
                        nx = Math.min(width-75, Math.max(0,ThreadLocalRandom.current().nextInt(b.x-30, b.x+30)));
                        ny = Math.min(height-75, Math.max(0, ThreadLocalRandom.current().nextInt(b.y-30, b.y+30)));
                    }while(isOccupied(nx, ny, nx+75, ny+75, b, walls, proletariats) != null);
                    b.x = nx;
                    b.y = ny;
                    announceChange(b);
                }
                Thread.sleep(30);
            }}catch(Exception e){}
        }).start();
        server = new ServerSocket(8000);
        new Thread(() -> {
            try {
                while (true) {
                    Socket s = server.accept();

                    new Thread(() -> {
                        try {
                            Propagandist p = new Propagandist(s);
                            synchronized (clients) {
                                clients.put(p, p.box);
                            }
                            p.initialize();
                            p.start();
                            announceChange(p);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void run() {
        int ps = 30;
        try {
            while (true) {
                synchronized (propagandas) {
                    for (Iterator<Propaganda> i = propagandas.iterator(); i.hasNext(); ) {
                        Propaganda b = i.next();
                        b.move(1);
                        int x = (int) b.getRay().origin.x;
                        int y = (int) b.getRay().origin.y;
                        if (x < -ps || y < -ps || x > width || y > height ||
                                isOccupied(x, y, x + ps, y + ps, null, walls) != null) {
                            i.remove();
                            announceRemove(b);
                            System.out.println("projectile gone " + b.id);
                        } else {
                            Box voter = isOccupied(x, y, x, y, null, proletariats);
                            if (voter != null) {
                                System.out.println("Influence from " + propagandaThrower.get(b).side);
                                influences[propagandaThrower.get(b).side].setScore(10);
                                influences[1 - propagandaThrower.get(b).side].setScore(-10);
                                i.remove();
                                announceRemove(b);
                                announceChange(influences[0]);
                                announceChange(influences[1]);
                                System.out.println("projectile hit " + b.id);
                            } else {
                                System.out.println("projectile moved " + b.id);
                                announceChange(b);
                            }
                        }
                    }
                }
                Thread.sleep(30);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void announceRemove(Object o) {
        synchronized (clients) {
            for (Propagandist c : clients.keySet()) {
                c.changed(o, false);
            }
        }
    }

    private void announceChange(Object o) {
        synchronized (clients) {
            for (Propagandist c : clients.keySet()) {
                c.changed(o, true);
            }
        }
    }

    private Box isOccupied(int x1, int y1, int x2, int y2, Box except, Collection<Box>... boxes) {
        for (Collection<Box> rr : boxes)
            for (Box r : rr) {
                if (r == except)
                    continue;
                if (x1 < r.x + r.width && x2 > r.x && y1 < r.y + r.height && y2 > r.y)
                    return r;
            }
        return null;
    }

    private void addPropaganda(Propagandist from, Propaganda propaganda) {
        synchronized (propagandas) {
            propagandas.add(propaganda);
            propagandaThrower.put(propaganda, from);
        }
        announceChange(propaganda);
    }

    private class Propagandist extends Thread {
        public final Box box;
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final String name;
        private final int side;
        private final HashMap<Object, Boolean> changes = new HashMap<>();
        // 2nd parameter: change if true, removes if false
        private long lastThrow = 0;

        public Propagandist(Socket s) throws Exception {
            socket = s;
            out = new DataOutputStream(s.getOutputStream());
            in = new DataInputStream(s.getInputStream());
            name = in.readUTF();
            side = in.readInt();
            if (side == 0) {
                box = new Box(100, height - 100, 75, 75);
            } else {
                box = new Box(width - 100, 100, 75, 75);
            }
        }

        public void initialize() throws IOException {
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(influences[0].getScore());
            out.writeInt(influences[1].getScore());

            out.writeInt(box.x);
            out.writeInt(box.y);
            out.writeInt(box.width);
            out.writeInt(box.height);

            out.writeInt(walls.size());
            for (Box w : walls) {
                out.writeInt(w.x);
                out.writeInt(w.y);
                out.writeInt(w.width);
                out.writeInt(w.height);
            }

            out.writeInt(proletariats.size());
            for (Box w : proletariats) {
                out.writeInt(w.id);
                out.writeInt(w.x);
                out.writeInt(w.y);
                out.writeInt(w.width);
                out.writeInt(w.height);
            }

            ArrayList<Propagandist> arr;
            synchronized (clients) {
                arr = new ArrayList<>(clients.keySet());
            }
            out.writeInt(arr.size());
            for (Propagandist c : arr) {
                out.writeInt(c.box.id);
                out.writeInt(c.side);
                out.writeUTF(c.name);
                out.writeInt(c.box.x);
                out.writeInt(c.box.y);
            }

            out.flush();

            new Thread(() -> {
                try {
                    while (true) {
                        HashMap<Object, Boolean> chg = new HashMap<>();
                        synchronized (changes) {
                            changes.wait();
                            chg.putAll(changes);
                            changes.clear();
                        }
                        for (Object o : chg.keySet()) {
                            out.writeBoolean(chg.get(o));
                            if (o instanceof Propaganda) {
                                Propaganda b = (Propaganda) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_BALL);
                                out.writeInt(b.id);
                                out.writeInt((int) b.getRay().origin.x);
                                out.writeInt((int) b.getRay().origin.y);
                            } else if (o instanceof Propagandist) {
                                Propagandist c = (Propagandist) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_CLIENT);
                                out.writeInt(c.box.id);
                                out.writeInt(c.side);
                                out.writeUTF(c.name);
                                out.writeInt(c.box.x);
                                out.writeInt(c.box.y);
                            } else if (o instanceof Influence) {
                                Influence sc = (Influence) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_SCORE);
                                out.writeInt(sc.party);
                                out.writeInt(sc.getScore());
                            } else if (o instanceof Box){
                                Box b = (Box) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_BOX);
                                out.writeInt(b.id);
                                out.writeInt(b.x);
                                out.writeInt(b.y);
                            }
                        }
                        out.flush();
                        chg.clear();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    socket.close();
                } catch (Exception e) {
                }
            }).start();
        }

        public void changed(Object another, boolean isedited) {
            synchronized (changes) {
                changes.put(another, isedited);
                changes.notify();
            }
        }

        private void move(int direction) {
            int dx = 0, dy = 0;
            int distance = 12;
            switch (direction) {
                case 0:
                    dy = 1;
                    break;
                case 1:
                    dx = 1;
                    break;
                case 2:
                    dy = -1;
                    break;
                case 3:
                    dx = -1;
                    break;
            }
            while (distance > 0 && box.x + dx * distance < 0) distance--;
            while (distance > 0 && box.y + dy * distance < 0) distance--;
            while (distance > 0 && box.x + box.width + dx * distance > width) distance--;
            while (distance > 0 && box.y + box.height + dy * distance > height) distance--;
            while (distance > 0 && null != isOccupied(
                    box.x + dx * distance, box.y + dy * distance, box.x + dx * distance + box.width, box.y + dy * distance + box.height,
                    this.box, walls, clients.values())) distance--;
            if (distance == 0) {
                // System.out.println("["+name+"] " + "dist=0");
                return;
            } else {
                // System.out.println("["+name+"] " + "dist=" + distance + " dir=" + direction + " dx=" + dx + " dy=" + dy);
            }
            box.move(dx * distance, dy * distance);
            announceChange(this);
        }

        private void throwPropaganda(int direction) {
            if (lastThrow + 200 > System.currentTimeMillis())
                return;
            lastThrow = System.currentTimeMillis();
            System.out.println("[" + name + "] " + "throw dir=" + direction);
            int dx = 0, dy = 0;
            int x = box.x, y = box.y;
            int distance = 17;
            switch (direction) {
                case 0:
                    dy = 1;
                    x += box.width / 2;
                    y += box.height;
                    break;
                case 1:
                    dx = 1;
                    x += box.width;
                    y += box.height / 2;
                    break;
                case 2:
                    dy = -1;
                    x += box.width / 2;
                    break;
                case 3:
                    dx = -1;
                    y += box.height / 2;
                    break;
            }
            addPropaganda(this, new Propaganda(x, y, dx * distance, dy * distance));
        }

        @Override
        public void run() {
            try {
                while (true) {
                    switch (in.readInt()) {
                        case MessageCodes.CLIENT_MOVE: {
                            move(in.readInt());
                            break;
                        }
                        case MessageCodes.CLIENT_THROW: {
                            throwPropaganda(in.readInt());
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            removeClient(this);
        }
    }

    private class Influence {
        public final int party;
        private int score;

        public Influence(int party) {
            this.party = party;
            this.score = 750;
        }

        public void setScore(int s) {
            score = Math.max(0, Math.min(1500, s + score));
        }

        public int getScore() {
            return score;
        }
    }
}
