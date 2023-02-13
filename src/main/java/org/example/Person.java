package org.example;

import lombok.*;

import javax.persistence.*;

@Entity
@Data
@NoArgsConstructor
@Table(name = "Person")
public class Person {
    @Column(name = "person_id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int person_id;
    @Column(name = "chat_id")
    private String chat_id;
    @Column(name = "points")
    private int points;

    @Column(name = "answer")
    private String answer;
    public Person(String chat_id, int points, String answer) {
        this.chat_id = chat_id;
        this.points = points;
    }
}
