package Compose;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import simulation.Inspiration;
import simulation.Box;

public class ServerMain extends Thread {

    private final int width, height;
    private final ArrayList<Box> walls = new ArrayList<>();
    private final ArrayList<Box> critics = new ArrayList<>();
    private final HashMap<Composer, Box> clients = new HashMap<>();
    private final ArrayList<Inspiration> inspirations = new ArrayList<>();
    private final WeakHashMap<Inspiration, Composer> inspirationThrower = new WeakHashMap<>();
    private final Influence[] influences = new Influence[]{
            new Influence(0), new Influence(1)
    };
    private final Box[] bases = new Box[2];
    private final ServerSocket server;

    public static void main(String[] args) throws Exception {
        new ServerMain().start();
        
    }

    private void removeClient(Composer c) {
        synchronized (clients) {
            clients.remove(c);
        }
        announceRemove(c);
    }

    private ServerMain() throws Exception {
        width = 1400;
        height = 750;
        bases[0] = new Box(0, height - 320, 320, 320);
        bases[1] = new Box(width - 320, 0, 320, 320);
        walls.add(new Box(0, height - 320, 240, 10));
        walls.add(new Box(320, height - 240, 10, 240));
        walls.add(new Box(width - 240, 320, 240, 10));
        walls.add(new Box(width - 320, 0, 10, 240));
        walls.add(new Box(width / 2 - 150, height / 2 - 150, 300, 10));
        walls.add(new Box(width / 2 - 150, height / 2 - 150, 10, 215));
        walls.add(new Box(width / 2 + 150, height / 2 - 150, 10, 300));
        walls.add(new Box(width / 2 - 150, height / 2 + 150, 300, 10));
        for (int i = 0; i < 10; i++) {
            Box b = new Box(-100, -100, 75, 75);
            int nx, ny;
            do {
                nx = ThreadLocalRandom.current().nextInt(0, width - 75);
                ny = ThreadLocalRandom.current().nextInt(0, height - 75);
            } while (isOccupied(nx, ny, nx + 75, ny + 75, b, walls, critics) != null);
            b.x = nx;
            b.y = ny;
            critics.add(b);
        }
        new Thread(() -> {
            try {
                while (true) {
                    for (Box b : critics) {
                        int nx, ny;
                        do {
                            nx = Math.min(width - 75, Math.max(0, ThreadLocalRandom.current().nextInt(b.x - 30, b.x + 30)));
                            ny = Math.min(height - 75, Math.max(0, ThreadLocalRandom.current().nextInt(b.y - 30, b.y + 30)));
                        } while (isOccupied(nx, ny, nx + 75, ny + 75, b, walls, critics) != null);
                        b.x = nx;
                        b.y = ny;
                        announceChange(b);
                    }
                    Thread.sleep(30);
                }
            } catch (Exception e) {
            }
        }).start();
        server = new ServerSocket(8000);
        new Thread(() -> {
            try {
                while (true) {
                    Socket s = server.accept();

                    new Thread(() -> {
                        try {
                            Composer p = new Composer(s);
                            synchronized (clients) {
                                clients.put(p, p.cubistRepresentation);
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
                synchronized (inspirations) {
                    for (Iterator<Inspiration> i = inspirations.iterator(); i.hasNext(); ) {
                        Inspiration b = i.next();
                        b.move(1);
                        int x = (int) b.getRay().origin.x;
                        int y = (int) b.getRay().origin.y;
                        if (x < -ps || y < -ps || x > width || y > height ||
                                isOccupied(x, y, x + ps, y + ps, null, walls) != null) {
                            i.remove();
                            announceRemove(b);
                            System.out.println("projectile gone " + b.id);
                        } else {
                            Box cli = isOccupied(x, y, x + ps, y + ps, null, clients.values());
                            if(cli != null){
                                Composer from = inspirationThrower.get(b);
                                Composer to = null;
                                for(Composer c : clients.keySet())
                                    if(clients.get(c) == cli)
                                        to = c;
                                if(to != null && to.side != from.side){
                                    i.remove();
                                    announceRemove(b);
                                    influences[from.side].setScore(+20);
                                    influences[to.side].setScore(-20);
                                    int nx, ny;
                                    do {
                                        nx = ThreadLocalRandom.current().nextInt(0, width - 75);
                                        ny = ThreadLocalRandom.current().nextInt(0, height - 75);
                                    } while (isOccupied(nx, ny, nx + 75, ny + 75,
                                            to.cubistRepresentation, walls, critics, clients.values(), excludebox(0)) != null);
                                    to.cubistRepresentation.x = nx;
                                    to.cubistRepresentation.y = ny;
                                    to.side = 0;
                                    announceChange(to, influences[0], influences[1]);
                                    synchronized (clients){
                                        boolean allCommie = true;
                                        for(Composer c : clients.keySet())
                                            allCommie &= c.side == 0;
                                        if(allCommie){
                                            ArrayList<Composer> c = new ArrayList<>(clients.keySet());
                                            Collections.shuffle(c);
                                            for(int ii =1; ii < c.size(); ii++){
                                                Composer cc = c.get(ii);
                                                cc.side = 1;
                                                cc.inspirationCount = 30;
                                                if(isOccupied(cc.cubistRepresentation.x, cc.cubistRepresentation.y,
                                                        cc.cubistRepresentation.x + 75, cc.cubistRepresentation.y + 75, cc.cubistRepresentation,
                                                        walls, critics, clients.values(), excludebox(1)) != null)
                                                    cc.relocate();
                                                announceChange(c.get(ii));
                                            }
                                            c.get(0).inspirationCount = 0;
                                            announceChange(c.get(0));
                                        }
                                    }
                                }else{
                                    System.out.println("projectile moved " + b.id);
                                    announceChange(b);
                                }
                            }else {
                                Box voter = isOccupied(x, y, x + ps, y + ps, null, critics);
                                if (voter != null) {
                                    System.out.println("Influence from " + inspirationThrower.get(b).side);
                                    influences[inspirationThrower.get(b).side].setScore(10);
                                    influences[1 - inspirationThrower.get(b).side].setScore(-10);
                                    i.remove();
                                    System.out.println("projectile hit " + b.id);
                                    int nx, ny;
                                    do {
                                        nx = ThreadLocalRandom.current().nextInt(0, width - 75);
                                        ny = ThreadLocalRandom.current().nextInt(0, height - 75);
                                    } while (isOccupied(nx, ny, nx + 75, ny + 75, voter, walls, critics) != null);
                                    voter.x = nx;
                                    voter.y = ny;
                                    announceRemove(b);
                                    announceChange(influences[0], influences[1], voter);
                                } else {
                                    System.out.println("projectile moved " + b.id);
                                    announceChange(b);
                                }
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

    private void announceRemove(Object... o) {
        synchronized (clients) {
            for (Composer c : clients.keySet()) {
                c.changed(o, false);
            }
        }
    }

    private void announceChange(Object... o) {
        synchronized (clients) {
            for (Composer c : clients.keySet()) {
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

    private void addInspiration(Composer from, Inspiration inspiration) {
        synchronized (inspirations) {
            if(from.inspirationCount <= 0)
                return;
            System.out.println();
            inspirations.add(inspiration);
            from.inspirationCount--;
            announceChange(from);
            inspirationThrower.put(inspiration, from);
        }
        announceChange(inspiration);
    }

    public List<Box> excludebox(int side){
        List<Box> b = new ArrayList<>();
        b.add(bases[1 - side]);
        return b;
    }

    private class Composer extends Thread {
        public final Box cubistRepresentation;
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final String name;
        private int side;
        public int inspirationCount = 0;
        public boolean enteredFactory = false;
        private final HashMap<Object, Boolean> changes = new HashMap<>();
        // 2nd parameter: change if true, removes if false
        private long lastThrow = 0;

        public Composer(Socket s) throws Exception {
            socket = s;
            out = new DataOutputStream(s.getOutputStream());
            in = new DataInputStream(s.getInputStream());
            name = in.readUTF();
            side = in.readInt();

            cubistRepresentation = new Box(0, 0, 75, 75);

            relocate();
        }

        public void relocate(){
            int x1, x2, y1, y2;
            if (side == 0) {
                x1 = 0; x2 = 300;
                y1 = height - 300; y2 = height - 75;
            } else {
                x1 = width - 300; x2 = width - 75;
                y1 = 0; y2 = 300;
            }

            int nx, ny;
            do {
                nx = ThreadLocalRandom.current().nextInt(x1, x2);
                ny = ThreadLocalRandom.current().nextInt(y1, y2);
            } while (isOccupied(nx, ny, nx + 75, ny + 75, cubistRepresentation, walls, critics, clients.values(), excludebox(side)) != null);
            cubistRepresentation.x = nx;
            cubistRepresentation.y = ny;
        }

        public void initialize() throws IOException {
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(influences[0].getScore());
            out.writeInt(influences[1].getScore());

            out.writeInt(cubistRepresentation.x);
            out.writeInt(cubistRepresentation.y);
            out.writeInt(cubistRepresentation.width);
            out.writeInt(cubistRepresentation.height);

            out.writeInt(walls.size());
            for (Box w : walls) {
                out.writeInt(w.x);
                out.writeInt(w.y);
                out.writeInt(w.width);
                out.writeInt(w.height);
            }

            out.writeInt(critics.size());
            for (Box w : critics) {
                out.writeInt(w.id);
                out.writeInt(w.x);
                out.writeInt(w.y);
                out.writeInt(w.width);
                out.writeInt(w.height);
            }

            ArrayList<Composer> arr;
            synchronized (clients) {
                arr = new ArrayList<>(clients.keySet());
            }
            out.writeInt(arr.size());
            for (Composer c : arr) {
                out.writeInt(c.cubistRepresentation.id);
                out.writeInt(c.side);
                out.writeUTF(c.name);
                out.writeInt(c.cubistRepresentation.x);
                out.writeInt(c.cubistRepresentation.y);
                out.writeInt(c.inspirationCount);
                out.writeBoolean(c.enteredFactory);
            }

            out.flush();

            new Thread(() -> {
                try {
                    while (true) {
                        HashMap<Object, Boolean> changes = new HashMap<>();
                        synchronized (this.changes) {
                            this.changes.wait();
                            changes.putAll(this.changes);
                            this.changes.clear();
                        }
                        for (Object o : changes.keySet()) {
                            out.writeBoolean(changes.get(o));
                            if (o instanceof Inspiration) {
                                Inspiration b = (Inspiration) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_BALL);
                                out.writeInt(b.id);
                                out.writeInt((int) b.getRay().origin.x);
                                out.writeInt((int) b.getRay().origin.y);
                            } else if (o instanceof Composer) {
                                Composer c = (Composer) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_CLIENT);
                                out.writeInt(c.cubistRepresentation.id);
                                out.writeInt(c.side);
                                out.writeUTF(c.name);
                                out.writeInt(c.cubistRepresentation.x);
                                out.writeInt(c.cubistRepresentation.y);
                                out.writeInt(c.inspirationCount);
                                out.writeBoolean(c.enteredFactory);
                            } else if (o instanceof Influence) {
                                Influence sc = (Influence) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_SCORE);
                                out.writeInt(sc.party);
                                out.writeInt(sc.getScore());
                            } else if (o instanceof Box) {
                                Box b = (Box) o;
                                out.writeInt(MessageCodes.SERVER_CHANGED_BOX);
                                out.writeInt(b.id);
                                out.writeInt(b.x);
                                out.writeInt(b.y);
                            }
                        }
                        out.flush();
                        changes.clear();
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

        public void changed(Object another[], boolean isedited) {
            synchronized (changes) {
                for(Object o : another)
                    changes.put(o, isedited);
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
            while (distance > 0 && cubistRepresentation.x + dx * distance < 0) distance--;
            while (distance > 0 && cubistRepresentation.y + dy * distance < 0) distance--;
            while (distance > 0 && cubistRepresentation.x + cubistRepresentation.width + dx * distance > width) distance--;
            while (distance > 0 && cubistRepresentation.y + cubistRepresentation.height + dy * distance > height) distance--;
            while (distance > 0 && null != isOccupied(
                    cubistRepresentation.x + dx * distance, cubistRepresentation.y + dy * distance, cubistRepresentation.x + dx * distance + cubistRepresentation.width, cubistRepresentation.y + dy * distance + cubistRepresentation.height,
                    this.cubistRepresentation, walls, clients.values(), excludebox(side))) distance--;
            if (distance == 0) {
                // System.out.println("["+name+"] " + "dist=0");
                return;
            } else {
                // System.out.println("["+name+"] " + "dist=" + distance + " dir=" + direction + " dx=" + dx + " dy=" + dy);
            }
            cubistRepresentation.move(dx * distance, dy * distance);
            boolean prev = enteredFactory;
            enteredFactory = ((cubistRepresentation.x>(1400/2.0-120)&& cubistRepresentation.x< (1400/2.0-120)+270&& cubistRepresentation.y>(750/2.0-150)&& cubistRepresentation.y< (750/2.0-150)+300));
            if(!prev && enteredFactory)
                inspirationCount+=5;
            announceChange(this);
        }

        private void throwInspiration(int direction) {
            if (lastThrow + 70 > System.currentTimeMillis())
                return;
            lastThrow = System.currentTimeMillis();
            System.out.println("[" + name + "] " + "throw dir=" + direction);
            int dx = 0, dy = 0;
            int x = cubistRepresentation.x, y = cubistRepresentation.y;
            int distance = 17;
            switch (direction) {
                case 0:
                    dy = 1;
                    x += cubistRepresentation.width / 2;
                    y += cubistRepresentation.height;
                    break;
                case 1:
                    dx = 1;
                    x += cubistRepresentation.width;
                    y += cubistRepresentation.height / 2;
                    break;
                case 2:
                    dy = -1;
                    x += cubistRepresentation.width / 2;
                    break;
                case 3:
                    dx = -1;
                    y += cubistRepresentation.height / 2;
                    break;
            }
            addInspiration(this, new Inspiration(x, y, dx * distance, dy * distance));
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
                            throwInspiration(in.readInt());
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
