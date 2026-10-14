import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { EventCard, FeaturedEventCard } from "../components/EventCard";
import type { ArenaEvent } from "../types";

afterEach(cleanup);
const event: ArenaEvent = {
  id: 494, status: "SCHEDULED", startsAt: "2026-10-20T12:00:00Z",
  sport: "Counter-Strike 2", championship: "Thunderpick World Championship · 2026",
  phase: "2026", externalProvider: "PANDASCORE", externalId: "1741358",
  homeCompetitor: { name: "Team Falcons" }, awayCompetitor: { name: "NIP" },
};

describe("competition headings", () => {
  it.each([EventCard, FeaturedEventCard])("preserves the complete name for hover and assistive text without repeating the edition", (Card) => {
    render(<MemoryRouter><Card event={event} /></MemoryRouter>);
    expect(screen.getByTitle(event.championship as string)).toHaveTextContent(event.championship as string);
    expect(screen.queryByText("2026", { exact: true })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ver detalhes/ })).toHaveAttribute("href", "/events/494");
  });

  it("does not repeat the same series with different capitalization or punctuation", () => {
    render(<MemoryRouter><EventCard compact event={{ ...event, championship: "Oceania Cup #3 season 52 2026", phase: "Oceania cup #3 season 52 2026" }} /></MemoryRouter>);
    expect(screen.getByTitle("Oceania Cup #3 season 52 2026")).toBeVisible();
    expect(screen.queryByText("Oceania cup #3 season 52 2026", { exact: true })).not.toBeInTheDocument();
  });

  it("keeps a distinct competition phase", () => {
    render(<MemoryRouter><EventCard event={{ ...event, phase: "Semifinal" }} /></MemoryRouter>);
    expect(screen.getByTitle("Semifinal")).toBeVisible();
  });
});
