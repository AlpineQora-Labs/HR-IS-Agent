/* The name the assistant goes by on every screen. It must equal app.assistant-name
   in the backend's application.yml (the name the assistant introduces itself by);
   it is not read from the server, so the two are changed together. "Aria" is only
   the module's name in the code. */
export const ASSISTANT = 'Bank of America Careers'
/* What the assistant's chat is called where the full name would be a mouthful. */
export const CHAT = 'Careers chat'
/* The assistant on the recruiter screens. It reads the portal's records and words a briefing; it is
   not a person and carries no person's name. */
export const PORTAL_ASSISTANT = 'Portal assistant'
