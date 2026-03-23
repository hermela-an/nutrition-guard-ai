import { Component, ElementRef, inject, ViewChild, ChangeDetectorRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

interface ChatMessage {
  text: string;
  isUser: boolean;
  time: Date;
  imageUrl?: string;
  imageErrored?: boolean;
  imageRetryCount?: number;
}

interface SuggestionChip {
  label: string;
  question: string;
  vitaminKey?: string;
}

@Component({
  selector: 'app-floating-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Floating toggle button -->
    <button class="chat-toggle-btn" (click)="toggleChat()" [class.active]="isOpen" aria-label="Toggle AI Assistant">
      <svg *ngIf="!isOpen" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="m3 21 1.9-5.7a8.5 8.5 0 1 1 3.8 3.8z"></path>
      </svg>
      <svg *ngIf="isOpen" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 6 6 18"></path>
        <path d="m6 6 12 12"></path>
      </svg>
    </button>

    <!-- Chat Window -->
    <div class="chat-window" [class.open]="isOpen">
      <div class="chat-header">
        <div class="chat-header-info">
          <div class="chat-avatar" aria-label="NutritionGuard AI">NG</div>
          <div>
            <h3>NutriGuard AI</h3>
            <span class="status-indicator">Always online</span>
          </div>
        </div>
      </div>

      <div class="chat-body" #chatBody>
        <!-- Session gate: always treat as a new session unless the user chooses Go Back -->
        <div class="chat-empty-state" *ngIf="sessionGateOpen">
          <div class="empty-icon" aria-hidden="true">&#128075;</div>
          <p>Welcome back! Do you want to continue where you left off or start a new conversation?</p>
          <div class="suggestion-chips session-actions">
            <button type="button" (click)="restorePreviousSession()" [disabled]="!hasPreviousSession">Go Back</button>
            <button type="button" class="primary" (click)="startFresh()">Start Fresh</button>
          </div>
          <span class="muted" *ngIf="!hasPreviousSession">No previous session found.</span>
        </div>

        <!-- Welcome / placeholder if no messages exist -->
        <div class="chat-empty-state" *ngIf="!sessionGateOpen && messages.length === 0">
          <div class="empty-icon" aria-hidden="true">&#128075;</div>
          <p>Hi there! I'm your personal nutrition assistant.</p>
          <span class="muted">Ask me about your diet, meals, or vitamin suggestions.</span>
          
          <div class="suggestion-chips quick-picks" aria-label="Quick questions">
            <button
              type="button"
              *ngFor="let chip of suggestionChips"
              (click)="quickAsk(chip.question, chip.vitaminKey)"
            >
              {{ chip.label }}
            </button>
          </div>
        </div>

        <div class="message-group" *ngFor="let msg of messages">
          <div class="message" [class.user-msg]="msg.isUser" [class.ai-msg]="!msg.isUser">
            <div class="message-bubble">
              <span [innerHTML]="formatText(msg.text)"></span>
              <img
                *ngIf="msg.imageUrl && !msg.imageErrored"
                [src]="msg.imageUrl"
                class="chat-img"
                alt="Attached resource"
                loading="lazy"
                crossorigin="anonymous"
                referrerpolicy="origin"
                (error)="handleImageError(msg)"
              >
              <img
                *ngIf="msg.imageUrl && msg.imageErrored"
                [src]="fallbackImageUrl"
                class="chat-img"
                alt="Image unavailable"
                loading="lazy"
                crossorigin="anonymous"
                referrerpolicy="origin"
                (error)="hideImage(msg)"
              >
            </div>
            <div class="message-time">{{ msg.time | date:'shortTime' }}</div>
          </div>
        </div>

        <div class="typing-indicator" *ngIf="isTyping">
          <span></span><span></span><span></span>
        </div>
      </div>

      <div class="chat-footer">
        <div class="quick-bar" *ngIf="!sessionGateOpen && messages.length > 0" aria-label="Quick vitamin shortcuts">
          <span class="quick-bar-label">Quick vitamins</span>
          <div class="quick-bar-chips">
            <button
              type="button"
              *ngFor="let chip of quickBarChips"
              (click)="quickAsk(chip.question, chip.vitaminKey)"
            >
              {{ chip.label }}
            </button>
          </div>
        </div>

        <div class="chat-input-row">
          <input 
            type="text" 
            [(ngModel)]="currentInput" 
            (keyup.enter)="sendMessage()"
            placeholder="Ask a nutrition question..." 
            class="chat-input"
            [disabled]="isTyping || sessionGateOpen"
          >
          <button class="send-btn" (click)="sendMessage()" [disabled]="!currentInput.trim() || isTyping || sessionGateOpen">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .chat-toggle-btn {
      position: fixed;
      bottom: 24px;
      right: 24px;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--brand);
      color: white;
      border: none;
      box-shadow: 0 4px 12px rgba(46, 159, 97, 0.3);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
      transition: transform 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275), background 0.2s;
    }

    .chat-toggle-btn:hover {
      transform: scale(1.05);
      background: #25824e;
    }

    .chat-toggle-btn.active {
      transform: rotate(90deg);
      background: #475a4b;
    }

    .chat-window {
      position: fixed;
      bottom: 90px;
      right: 24px;
      width: 350px;
      height: 500px;
      max-height: calc(100vh - 120px);
      background: #ffffff;
      border-radius: 16px;
      box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12), 0 0 1px rgba(0,0,0,0.2);
      display: flex;
      flex-direction: column;
      z-index: 9998;
      overflow: hidden;
      
      opacity: 0;
      transform: translateY(20px) scale(0.95);
      pointer-events: none;
      transition: opacity 0.3s ease, transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }

    .chat-window.open {
      opacity: 1;
      transform: translateY(0) scale(1);
      pointer-events: all;
    }

    .chat-header {
      background: #f4f7f3;
      padding: 16px;
      border-bottom: 1px solid #e6ede3;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .chat-header-info {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .chat-avatar {
      width: 36px;
      height: 36px;
      background: white;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      font-weight: 900;
      letter-spacing: 0.5px;
      color: var(--brand);
      box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    }

    .chat-header h3 {
      margin: 0;
      font-size: 15px;
      font-weight: 800;
      color: var(--text);
    }

    .status-indicator {
      font-size: 11px;
      color: var(--muted);
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .status-indicator::before {
      content: '';
      display: inline-block;
      width: 6px;
      height: 6px;
      background: #2e9f61;
      border-radius: 50%;
    }

    .chat-body {
      flex: 1;
      padding: 16px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 16px;
      background: #fafcf9;
      scroll-behavior: smooth;
    }

    .chat-empty-state {
      text-align: center;
      margin: auto 0;
      padding: 20px;
    }

    .empty-icon {
      width: 56px;
      height: 56px;
      margin: 0 auto 12px;
      border-radius: 50%;
      background: #ffffff;
      border: 1px solid #e6ede3;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 30px;
    }

    .chat-empty-state p {
      margin: 0 0 4px;
      font-weight: 600;
      font-size: 14px;
    }

    .chat-empty-state .muted {
      font-size: 12px;
    }

    .suggestion-chips {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
      width: 100%;
      max-width: 320px;
      margin: 16px auto 0;
    }

    .suggestion-chips button {
      background: white;
      border: 1px solid #d8dfcf;
      padding: 10px 12px;
      border-radius: 999px;
      font-size: 12px;
      color: var(--brand);
      cursor: pointer;
      transition: all 0.2s;
      width: 100%;
      text-align: center;
      white-space: normal;
      line-height: 1.2;
      min-height: 38px;
    }

    @media (max-width: 360px) {
      .suggestion-chips {
        grid-template-columns: 1fr;
        max-width: 260px;
      }
    }

    .suggestion-chips button:hover {
      background: #f4f7f3;
      border-color: #2e9f61;
    }

    .suggestion-chips.session-actions button.primary {
      background: var(--brand);
      border-color: rgba(46, 159, 97, 0.35);
      color: #fff;
    }

    .suggestion-chips.session-actions {
      display: flex;
      justify-content: center;
      gap: 10px;
      max-width: none;
    }

    .message {
      display: flex;
      flex-direction: column;
      max-width: 85%;
    }

    .user-msg {
      align-self: flex-end;
    }

    .ai-msg {
      align-self: flex-start;
    }

    .message-bubble {
      padding: 10px 14px;
      border-radius: 12px;
      font-size: 14px;
      line-height: 1.4;
      word-wrap: break-word;
    }

    .user-msg .message-bubble {
      background: var(--brand);
      color: white;
      border-bottom-right-radius: 4px;
    }

    .ai-msg .message-bubble {
      background: white;
      border: 1px solid #e6ede3;
      color: var(--text);
      border-bottom-left-radius: 4px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.02);
    }

    .chat-img {
      max-width: 100%;
      border-radius: 8px;
      margin-top: 8px;
      display: block;
      border: 1px solid #e6ede3;
      object-fit: cover;
      max-height: 180px;
    }

    .message-time {
      font-size: 10px;
      color: #9aa89b;
      margin-top: 4px;
    }

    .user-msg .message-time {
      align-self: flex-end;
    }

    .chat-footer {
      padding: 12px 16px;
      background: white;
      border-top: 1px solid #e6ede3;
      display: flex;
      flex-direction: column;
      gap: 10px;
      align-items: stretch;
    }

    .chat-input-row {
      display: flex;
      gap: 8px;
      align-items: center;
      width: 100%;
    }

    .quick-bar {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .quick-bar-label {
      font-size: 11px;
      color: var(--muted);
      font-weight: 800;
      letter-spacing: 0.2px;
      white-space: nowrap;
    }

    .quick-bar-chips {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 2px;
      -webkit-overflow-scrolling: touch;
      scrollbar-width: thin;
    }

    .quick-bar-chips button {
      flex: 0 0 auto;
      background: #f4f7f3;
      border: 1px solid #d8dfcf;
      padding: 6px 10px;
      border-radius: 999px;
      font-size: 12px;
      color: var(--brand);
      cursor: pointer;
      transition: all 0.2s;
      white-space: nowrap;
    }

    .quick-bar-chips button:hover {
      background: #ffffff;
      border-color: #2e9f61;
    }

    .chat-input {
      flex: 1;
      background: #f4f7f3;
      border: 1px solid transparent;
      padding: 10px 16px;
      border-radius: 999px;
      font-size: 14px;
      outline: none;
      transition: all 0.2s;
    }

    .chat-input:focus {
      border-color: #d8dfcf;
      background: white;
      box-shadow: 0 0 0 2px rgba(46, 159, 97, 0.1);
    }

    .chat-input:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .send-btn {
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background: var(--brand);
      color: white;
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background 0.2s;
    }

    .send-btn:hover:not(:disabled) {
      background: #25824e;
    }

    .send-btn:disabled {
      background: #d8dfcf;
      cursor: not-allowed;
    }

    /* Typing animation */
    .typing-indicator {
      align-self: flex-start;
      background: white;
      border: 1px solid #e6ede3;
      padding: 12px 16px;
      border-radius: 12px;
      border-bottom-left-radius: 4px;
      display: flex;
      gap: 4px;
      width: fit-content;
    }

    .typing-indicator span {
      width: 6px;
      height: 6px;
      background: #9aa89b;
      border-radius: 50%;
      animation: bounce 1.4s infinite ease-in-out both;
    }

    .typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
    .typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

    @keyframes bounce {
      0%, 80%, 100% { transform: scale(0); }
      40% { transform: scale(1); }
    }

    /* Mobile handling */
    @media (max-width: 480px) {
      .chat-window {
        bottom: 0;
        right: 0;
        width: 100%;
        height: 100vh;
        max-height: 100vh;
        border-radius: 0;
      }

      .chat-toggle-btn.active {
        display: none; /* Hide toggle when fullscreen */
      }
    }
  `]
})
export class FloatingChatComponent implements AfterViewChecked {
  private static readonly SESSION_STORAGE_KEY = 'ng_chat_previous_session_v1';

  @ViewChild('chatBody') private chatBody!: ElementRef;

  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  isOpen = false;
  isTyping = false;
  currentInput = '';
  shouldScroll = false;

  sessionGateOpen = false;
  hasPreviousSession = false;
  private sessionChoice: 'go_back' | 'start_fresh' | null = null;
  private previousSessionMessages: ChatMessage[] = [];

  readonly suggestionChips: SuggestionChip[] = [
    {
      label: 'Vitamin A',
      vitaminKey: 'vitamin_a',
      question: 'Show me foods in our database rich in Vitamin A.'
    },
    {
      label: 'Vitamin D',
      vitaminKey: 'vitamin_d',
      question: 'Show me foods in our database rich in Vitamin D.'
    },
    {
      label: 'Vitamin E',
      vitaminKey: 'vitamin_e',
      question: 'Show me foods in our database rich in Vitamin E.'
    },
    {
      label: 'Vitamin K',
      vitaminKey: 'vitamin_k',
      question: 'Show me foods in our database rich in Vitamin K.'
    },
    {
      label: 'Vitamin B1 (Thiamine)',
      vitaminKey: 'vitamin_b1',
      question: 'Show me foods in our database rich in Vitamin B1 (Thiamine).'
    },
    {
      label: 'Vitamin B2 (Riboflavin)',
      vitaminKey: 'vitamin_b2',
      question: 'Show me foods in our database rich in Vitamin B2 (Riboflavin).'
    },
    {
      label: 'Vitamin B3 (Niacin)',
      vitaminKey: 'vitamin_b3',
      question: 'Show me foods in our database rich in Vitamin B3 (Niacin).'
    },
    {
      label: 'Vitamin B5 (Pantothenic Acid)',
      vitaminKey: 'vitamin_b5',
      question: 'Show me foods in our database rich in Vitamin B5 (Pantothenic Acid).'
    },
    {
      label: 'Vitamin B6 (Pyridoxine)',
      vitaminKey: 'vitamin_b6',
      question: 'Show me foods in our database rich in Vitamin B6 (Pyridoxine).'
    },
    {
      label: 'Vitamin B7 (Biotin)',
      vitaminKey: 'vitamin_b7',
      question: 'Show me foods in our database rich in Vitamin B7 (Biotin).'
    },
    {
      label: 'Vitamin B9 (Folate / Folic Acid)',
      vitaminKey: 'vitamin_b9',
      question: 'Show me foods in our database rich in Vitamin B9 (Folate / Folic Acid).'
    },
    {
      label: 'Vitamin B12 (Cobalamin)',
      vitaminKey: 'vitamin_b12',
      question: 'Show me foods in our database rich in Vitamin B12 (Cobalamin).'
    },
    {
      label: 'Vitamin C (Ascorbic Acid)',
      vitaminKey: 'vitamin_c',
      question: 'Show me foods in our database rich in Vitamin C (Ascorbic Acid).'
    }
  ];

  readonly quickBarChips: SuggestionChip[] = [
    {
      label: 'Vitamin A',
      vitaminKey: 'vitamin_a',
      question: 'Show me foods in our database rich in Vitamin A.'
    },
    {
      label: 'Vitamin D',
      vitaminKey: 'vitamin_d',
      question: 'Show me foods in our database rich in Vitamin D.'
    },
    {
      label: 'Vitamin C',
      vitaminKey: 'vitamin_c',
      question: 'Show me foods in our database rich in Vitamin C (Ascorbic Acid).'
    },
    {
      label: 'Vitamin B12',
      vitaminKey: 'vitamin_b12',
      question: 'Show me foods in our database rich in Vitamin B12 (Cobalamin).'
    },
    {
      label: 'Folate (B9)',
      vitaminKey: 'vitamin_b9',
      question: 'Show me foods in our database rich in Vitamin B9 (Folate / Folic Acid).'
    },
    {
      label: 'Vitamin E',
      vitaminKey: 'vitamin_e',
      question: 'Show me foods in our database rich in Vitamin E.'
    }
  ];

  readonly fallbackImageUrl =
    'https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80';

  messages: ChatMessage[] = [];

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  toggleChat(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.enterChatSession();
    }
    if (this.isOpen && this.messages.length > 0) {
      this.shouldScroll = true;
    }
  }

  private enterChatSession(): void {
    // Persist whatever is currently visible so it can be restored via "Go Back".
    if (this.messages.length > 0) {
      this.persistPreviousSession(this.messages);
    }

    this.previousSessionMessages = this.loadPreviousSession();
    this.hasPreviousSession = this.previousSessionMessages.length > 0;

    // Always behave as a new session until the user explicitly chooses "Go Back".
    this.sessionChoice = null;
    this.sessionGateOpen = true;
    this.messages = [];
    this.currentInput = '';
    this.isTyping = false;
    this.shouldScroll = true;
  }

  restorePreviousSession(): void {
    if (!this.hasPreviousSession) return;
    this.messages = this.previousSessionMessages.map(m => ({ ...m }));
    this.sessionChoice = 'go_back';
    this.sessionGateOpen = false;
    this.shouldScroll = true;
  }

  startFresh(): void {
    this.clearPreviousSession();
    this.previousSessionMessages = [];
    this.hasPreviousSession = false;
    this.messages = [];
    this.sessionChoice = 'start_fresh';
    this.sessionGateOpen = false;
    this.currentInput = '';
    this.shouldScroll = true;
  }

  quickAsk(question: string, vitaminKey?: string): void {
    this.sendMessageWithPayload(question, vitaminKey ? { vitaminKey, limit: 15 } : undefined);
  }

  sendMessage(): void {
    const text = this.currentInput.trim();
    if (!text || this.isTyping) return;
    this.sendMessageWithPayload(text);
    this.currentInput = '';
  }

  private sendMessageWithPayload(text: string, extra?: Record<string, unknown>): void {
    if (!text || this.isTyping) return;
    if (this.sessionGateOpen) return;

    // Add user message to UI immediately
    this.messages.push({
      text: text,
      isUser: true,
      time: new Date()
    });
    this.persistIfChosen();
    
    this.isTyping = true;
    this.shouldScroll = true;

    // Call REST endpoint mapped safely across Vert.x Event Bus
    const payload = { question: text, ...(extra ?? {}) };
    this.api.post<{ answer?: string, response?: string, imageUrl?: string, error?: string }>('/api/chat/ask', payload, this.auth.authHeaders())
      .subscribe({
        next: (resp) => {
          this.isTyping = false;
          // Different bots return different payload properties, handle safely
          const aiResponse = resp.answer || resp.response || "I'm sorry, I couldn't process that request at the moment.";
          
          this.messages.push({
            text: aiResponse,
            isUser: false,
            time: new Date(),
            imageUrl: (resp.imageUrl ?? '').trim() || undefined,
            imageErrored: false,
            imageRetryCount: 0
          });
          this.persistIfChosen();
          this.shouldScroll = true;
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.isTyping = false;
          this.messages.push({
            text: 'I lost connection to the server. Please check your backend and try again.',
            isUser: false,
            time: new Date()
          });
          this.persistIfChosen();
          this.shouldScroll = true;
          this.cdr.detectChanges();
        }
      });
  }

  private persistIfChosen(): void {
    // Once the user chooses "Start Fresh" or "Go Back", persist the active conversation
    // so it can be restored next time via the session gate.
    if (!this.sessionChoice) return;
    this.persistPreviousSession(this.messages);
  }

  private persistPreviousSession(messages: ChatMessage[]): void {
    try {
      const payload = messages.map(m => ({
        ...m,
        time: (m.time instanceof Date ? m.time : new Date(m.time)).toISOString()
      }));
      localStorage.setItem(FloatingChatComponent.SESSION_STORAGE_KEY, JSON.stringify(payload));
    } catch {
      // Ignore storage errors (e.g., private mode / storage disabled).
    }
  }

  private loadPreviousSession(): ChatMessage[] {
    try {
      const raw = localStorage.getItem(FloatingChatComponent.SESSION_STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw) as Array<Omit<ChatMessage, 'time'> & { time: string }>;
      if (!Array.isArray(parsed)) return [];
      return parsed
        .filter(m => typeof m?.text === 'string' && typeof m?.isUser === 'boolean')
        .map(m => ({
          text: m.text,
          isUser: m.isUser,
          time: new Date(m.time),
          imageUrl: m.imageUrl,
          imageErrored: m.imageErrored,
          imageRetryCount: m.imageRetryCount
        }));
    } catch {
      return [];
    }
  }

  private clearPreviousSession(): void {
    try {
      localStorage.removeItem(FloatingChatComponent.SESSION_STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  formatText(text: string): string {
    if (!text) return '';
    // Format basic markdown bolding
    return text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  }

  private scrollToBottom(): void {
    try {
      this.chatBody.nativeElement.scrollTop = this.chatBody.nativeElement.scrollHeight;
    } catch (err) {}
  }

  handleImageError(msg: ChatMessage): void {
    const attempts = msg.imageRetryCount ?? 0;
    if (attempts < 1 && msg.imageUrl) {
      msg.imageRetryCount = attempts + 1;
      msg.imageErrored = false;
      msg.imageUrl = this.withCacheBust(msg.imageUrl);
      this.cdr.detectChanges();
      return;
    }
    msg.imageErrored = true;
    this.cdr.detectChanges();
  }

  hideImage(msg: ChatMessage): void {
    msg.imageUrl = undefined;
    msg.imageErrored = false;
    msg.imageRetryCount = 0;
    this.cdr.detectChanges();
  }

  private withCacheBust(url: string): string {
    const trimmed = (url ?? '').trim();
    if (!trimmed) return trimmed;
    const separator = trimmed.includes('?') ? '&' : '?';
    return `${trimmed}${separator}ng_retry=${Date.now()}`;
  }
}
