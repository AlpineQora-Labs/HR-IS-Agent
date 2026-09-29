import { useEffect } from 'react'
import { useParams } from 'react-router-dom'
import PhoneSimulator from '@/components/PhoneSimulator'
import { useCandidateMessages } from '@/api/hooks'

/* The candidate's phone on a page of its own, so it can sit in a second
   window beside the console while the journey is shown. */
export default function PhonePage() {
  const { candidateId } = useParams()
  const { data } = useCandidateMessages(candidateId)
  const name = data?.candidateName

  useEffect(() => {
    const before = document.title
    document.title = name ? `${name} · phone` : 'Candidate phone'
    return () => {
      document.title = before
    }
  }, [name])

  return (
    <div className="phone-page">
      <h1 className="phone-page__title">{name ? `${name}’s phone` : 'Candidate’s phone'}</h1>
      <p className="phone-page__sub">A stand-in for the handset. Texts are handled as texts from a carrier are.</p>
      <PhoneSimulator candidateId={candidateId} tall />
    </div>
  )
}
