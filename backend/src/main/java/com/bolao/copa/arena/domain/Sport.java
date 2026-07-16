package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.SportCategory;
import jakarta.persistence.*;

@Entity
@Table(name = "arena_sports")
public class Sport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private SportCategory category;
    @Column(length = 80)
    private String icon;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private int displayOrder;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SportCategory getCategory() { return category; }
    public void setCategory(SportCategory category) { this.category = category; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
