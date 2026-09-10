export type UserRole = "ADMIN" | "PARTICIPANTE";

export interface User {
  userId: number;
  name: string;
  email: string;
  role: UserRole;
  avatarUrl?: string | null;
}

export interface AuthSession extends User {
  token: string;
}

export type EventStatus =
  | "SCHEDULED"
  | "OPEN"
  | "LIVE"
  | "FINISHED"
  | "CANCELLED"
  | "POSTPONED"
  | "AGENDADO"
  | "ABERTO"
  | "AO_VIVO"
  | "ENCERRADO"
  | "CANCELADO"
  | "ADIADO";

export interface Sport {
  id: number | string;
  name: string;
  slug?: string;
  code?: string;
  icon?: string;
  category?: "SPORT" | "ESPORT" | string;
  active?: boolean;
}

export interface Championship {
  id: number | string;
  name: string;
  sportId?: number | string;
  season?: string;
  logoUrl?: string | null;
}

export interface PredictionOption {
  id: number | string;
  key?: string;
  name?: string;
  label?: string;
  multiplier: number | string;
  suspended?: boolean;
  active?: boolean;
  result?: "WINNER" | "LOSER" | "VOID" | null;
}

export interface PredictionMarket {
  id: number | string;
  name: string;
  code?: string;
  status?: string;
  minimumPoints?: number;
  category?: string;
  templateCode?: string | null;
  timingMode?: "PRE_MATCH_ONLY" | "LIVE_ENABLED" | "LIVE_ONLY";
  opensAt?: string | null;
  closesAt?: string | null;
  availability?: MarketAvailability;
  settlementDescription?: string;
  options: PredictionOption[];
}

export interface MarketAvailability {
  allowed: boolean;
  code: string;
  label: string;
  reason: string;
}

export interface ResultField {
  key: string;
  label: string;
  group: string;
  type: "number" | "select";
  required: boolean;
  description?: string;
  options?: Array<{ value: string; label: string }>;
}

export interface MarketTemplate {
  code: string;
  name: string;
  category: string;
  timingMode: string;
  settlementDescription?: string;
}

export interface EventCompetitor {
  id?: number | string;
  name: string;
  shortName?: string;
  code?: string;
  logoUrl?: string | null;
  imageUrl?: string | null;
  score?: number | string | null;
}

export interface EventParticipant {
  id?: number | string;
  competitor: EventCompetitor;
  competitorId?: number | string;
  displayOrder?: number;
  position?: number | null;
  scoreLabel?: string | null;
}

export interface ArenaEvent {
  id: number | string;
  championshipId?: number | string;
  title?: string;
  sport?: Sport | string;
  sportName?: string;
  championship?: Championship | string;
  championshipName?: string;
  home?: EventCompetitor;
  away?: EventCompetitor;
  homeTeam?: EventCompetitor;
  awayTeam?: EventCompetitor;
  homeCompetitor?: EventCompetitor;
  awayCompetitor?: EventCompetitor;
  competitors?: EventCompetitor[];
  participants?: EventParticipant[];
  homeScore?: number | string | null;
  awayScore?: number | string | null;
  startsAt: string;
  predictionDeadline?: string;
  predictionClosesAt?: string;
  status: EventStatus | string;
  featured?: boolean;
  demoLiveData?: boolean;
  demo?: boolean;
  venue?: string;
  broadcast?: string;
  phase?: string;
  format?: string;
  liveClock?: string;
  clock?: string;
  period?: string;
  liveData?: string;
  score?: string;
  markets?: PredictionMarket[];
  availableMarketCount?: number;
  predictionAvailabilityLabel?: string;
  resultData?: Record<string, string>;
  resultSchema?: ResultField[];
  statistics?: Record<string, number | string>;
}

export type PredictionStatus =
  | "PENDING"
  | "ACTIVE"
  | "WON"
  | "LOST"
  | "CANCELLED"
  | "REFUNDED"
  | "PENDENTE"
  | "ATIVO"
  | "VENCEDOR"
  | "PERDEDOR"
  | "CANCELADO"
  | "REEMBOLSADO";

export interface Prediction {
  id: number | string;
  eventId: number | string;
  eventTitle?: string;
  marketId?: number | string;
  marketName?: string;
  optionId?: number | string;
  optionName?: string;
  optionLabel?: string;
  stakePoints?: number;
  points?: number;
  multiplier?: number | string;
  potentialPoints?: number;
  rewardPoints?: number;
  rewardedPoints?: number;
  status: PredictionStatus | string;
  createdAt?: string;
  placedAt?: string;
  canCancel?: boolean;
  poolId?: number | string | null;
}

export interface Wallet {
  balance?: number;
  balancePoints?: number;
  availablePoints?: number;
  earnedPoints?: number;
  usedPoints?: number;
  lifetimeEarned?: number;
  lifetimeUsed?: number;
  virtualPointsNotice?: string;
}

export interface WalletTransaction {
  id: number | string;
  type: string;
  description?: string;
  amount?: number;
  points?: number;
  balanceAfter?: number;
  createdAt?: string;
}

export interface Pool {
  id: number | string;
  name: string;
  description?: string;
  sport?: Sport | string;
  championship?: Championship | string;
  privacy?: "PUBLIC" | "PRIVATE" | string;
  publicPool?: boolean;
  ownerName?: string;
  virtualPrizePoints?: number;
  rules?: string;
  inviteCode?: string;
  participants?: number;
  participantCount?: number;
  maxParticipants?: number;
  status?: string;
  startsAt?: string;
  endsAt?: string;
  joined?: boolean;
  owner?: boolean;
  poolType?: "POOL" | "LEAGUE" | string;
  recurring?: boolean;
  sportId?: number | string;
  championshipId?: number | string;
}

export interface RankingRow {
  position: number;
  userId?: number | string;
  name?: string;
  participant?: string;
  playerName?: string;
  avatarUrl?: string;
  points: number;
  hits?: number;
  correctPredictions?: number;
  predictions?: number;
  totalPredictions?: number;
  accuracy?: number;
  streak?: number;
  movement?: number;
  currentUser?: boolean;
}

export interface Notification {
  id: number | string;
  type?: string;
  title: string;
  message?: string;
  read?: boolean;
  readAt?: string | null;
  createdAt?: string;
  link?: string;
  targetUrl?: string;
}

export interface Achievement {
  id: number | string;
  code?: string;
  name: string;
  description: string;
  unlocked?: boolean;
  unlockedAt?: string;
  progress?: number;
  target?: number;
  rarity?: string;
  pointsReward?: number;
}

export interface Challenge {
  id: number | string;
  name: string;
  description: string;
  progress?: number;
  target?: number;
  rewardPoints?: number;
  expiresAt?: string;
  completed?: boolean;
}

export interface DashboardData {
  greeting?: string;
  level?: number | string;
  levelTitle?: string;
  xp?: number;
  nextLevelXp?: number;
  points?: number;
  availablePoints?: number;
  rankingPosition?: number;
  position?: number;
  streak?: number;
  bestStreak?: number;
  accuracy?: number;
  activePredictions?: number;
  settledPredictions?: number;
  finishedPredictions?: number;
  wonPredictions?: number;
  featuredEvents?: ArenaEvent[];
  liveEvents?: ArenaEvent[];
  upcomingEvents?: ArenaEvent[];
  predictions?: Prediction[];
  recentPredictions?: Prediction[];
  pools?: Pool[];
  activePools?: Pool[] | number;
  ranking?: RankingRow[];
  weeklyRanking?: RankingRow[];
  playerName?: string;
  unreadNotifications?: number;
  challenges?: Challenge[];
  recentAchievements?: Achievement[];
  performance?: Array<{ label?: string; date?: string; value?: number; accuracy?: number }>;
  favoriteSports?: Array<{ name: string; accuracy?: number; predictions?: number }>;
  virtualPointsNotice?: string;
  [key: string]: unknown;
}

export interface ProfilePreferences {
  theme: "dark" | "light" | string;
  language: "pt-BR" | "en-US" | string;
  notifications: boolean;
  publicProfile: boolean;
}

export interface PlayerProfile extends ProfilePreferences {
  userId: number;
  name: string;
  email: string;
  role: UserRole | string;
  avatarUrl?: string | null;
  bio?: string | null;
  favoriteSports: string[];
  level: number;
  xp: number;
  points: number;
  createdAt?: string;
}

export interface CommunityAuthor {
  id?: number | string;
  name?: string;
  avatarUrl?: string | null;
}

export interface CommunityPost {
  id: number | string;
  author?: CommunityAuthor;
  authorName?: string;
  avatarUrl?: string | null;
  content?: string;
  topic?: string;
  createdAt?: string;
  updatedAt?: string;
  likeCount?: number;
  commentCount?: number;
  likedByCurrentUser?: boolean;
  ownedByCurrentUser?: boolean;
}

export interface CommunityComment {
  id: number | string;
  postId?: number | string;
  author?: CommunityAuthor;
  content?: string;
  createdAt?: string;
  ownedByCurrentUser?: boolean;
}

export interface PageResponse<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export interface PredictionDraft {
  event: ArenaEvent;
  market: PredictionMarket;
  option: PredictionOption;
}

export interface AdminDashboard {
  users?: number;
  activeUsers?: number;
  activeEvents?: number;
  liveEvents?: number;
  upcomingEvents?: number;
  predictionsToday?: number;
  pointsMovedToday?: number;
  pointsMoved?: number;
  openMarkets?: number;
  activePools?: number;
  processingErrors?: number;
  pendingResults?: number;
  eventsAwaitingResult?: number;
  recentAudit?: Array<{ id: number | string; action: string; actor?: string; createdAt?: string }>;
}
