package net.greypanther.repomirror;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Serialize;

@Entity
public final class MirroredEntity {
    @Id
    Long id;

    Date retrievedAt;
    String url;
    boolean successfullyRetrieved;
    @Serialize(zip = true, compressionLevel = 9)
    byte[] body;

    public MirroredEntity() {
    }

    MirroredEntity(String url) {
        this.retrievedAt = new Date();
        this.url = url;
        this.successfullyRetrieved = false;
    }

    MirroredEntity(String url, byte[] body) {
        this.retrievedAt = new Date();
        this.url = url;
        this.successfullyRetrieved = true;
        this.body = body;
    }
}
